package com.lingxi.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.agent.llm.ChatModelFactory;
import com.lingxi.agent.memory.ConversationMemoryService;
import com.lingxi.agent.tools.AgentToolSet;
import com.lingxi.common.exception.BizException;
import com.lingxi.config.AppProperties;
import com.lingxi.modules.chat.ChatMessage;
import com.lingxi.modules.chat.ChatService;
import com.lingxi.modules.chat.ChatSession;
import com.lingxi.common.util.ToolResultUnwrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Agent 编排核心：自研 ReAct 循环。
 * <p>
 * 与框架默认的"内部工具执行"不同，这里关闭 internalToolExecutionEnabled，
 * 由编排器自己驱动：流式拉取模型增量 → 聚合工具调用 → SSE 透出过程事件 →
 * 执行工具 → 将工具结果拼回上下文继续下一轮，直至模型产出最终答案或达到轮次上限。
 */
@Slf4j
@Service
public class AgentOrchestrator {

    private static final long SSE_TIMEOUT_MS = 300_000L;
    private static final int MAX_TOOL_RESULT_LOG = 4000;

    private final ChatModelFactory chatModelFactory;
    private final AgentToolSet toolSet;
    private final AgentRegistry registry;
    private final ConversationMemoryService memory;
    private final ConversationPersistence persistence;
    private final ChatService chatService;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;
    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
    private final Executor executor;

    public AgentOrchestrator(ChatModelFactory chatModelFactory, AgentToolSet toolSet, AgentRegistry registry,
                             ConversationMemoryService memory, ConversationPersistence persistence,
                             ChatService chatService, ObjectMapper objectMapper,
                             AppProperties properties,
                             @Qualifier("agentExecutor") Executor executor) {
        this.chatModelFactory = chatModelFactory;
        this.toolSet = toolSet;
        this.registry = registry;
        this.memory = memory;
        this.persistence = persistence;
        this.chatService = chatService;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.executor = executor;
    }

    /** 提交流式推理任务并立即返回 SSE 通道。 */
    public SseEmitter startStream(ChatSession session, AgentType agentType) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onTimeout(emitter::complete);
        executor.execute(() -> run(session, agentType, emitter));
        return emitter;
    }

    private void run(ChatSession session, AgentType agentType, SseEmitter emitter) {
        long startedAt = System.currentTimeMillis();
        String messageId = String.valueOf(session.getId()) + "-" + startedAt;
        try {
            send(emitter, "start", Map.of("agent", agentType.code(), "agentName", agentType.getDisplayName()),
                    session.getId(), messageId, 0);

            List<Message> instructions = new ArrayList<>();
            instructions.add(new SystemMessage(registry.systemPrompt(agentType)));
            instructions.addAll(memory.buildWindow(session.getId()));

            List<ToolCallback> callbacks = toolSet.forAgent(agentType);
            ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                    .toolCallbacks(callbacks)
                    .internalToolExecutionEnabled(false)
                    .build();

            ChatModel model = chatModelFactory.getChatModel();
            int maxIterations = properties.getAgent().getMaxIterations();
            String finalAnswer = null;
            int round = 0;

            for (; round < maxIterations; round++) {
                final int currentRound = round;
                Prompt prompt = new Prompt(instructions, options);
                StringBuilder roundText = new StringBuilder();
                ToolCallAccumulator accumulator = new ToolCallAccumulator();

                model.stream(prompt)
                        .doOnNext(response -> handleChunk(response, roundText, accumulator,
                                emitter, session.getId(), messageId, currentRound))
                        .blockLast(Duration.ofSeconds(120));

                List<AssistantMessage.ToolCall> toolCalls = accumulator.complete();
                if (toolCalls.isEmpty()) {
                    finalAnswer = roundText.toString();
                    break;
                }

                emitToolCalls(emitter, session.getId(), messageId, round, toolCalls);
                AssistantMessage assistantMessage =
                        new AssistantMessage(roundText.toString(), Map.of(), toolCalls);
                ChatResponse assistantResponse =
                        new ChatResponse(List.of(new Generation(assistantMessage)));

                ToolExecutionResult executionResult =
                        toolCallingManager.executeToolCalls(prompt, assistantResponse);
                List<Message> updatedMessages = executionResult.conversationHistory();

                Message tail = updatedMessages.get(updatedMessages.size() - 1);
                if (tail instanceof ToolResponseMessage toolResponseMessage) {
                    emitToolResults(emitter, session.getId(), messageId, round, toolResponseMessage);
                    saveMessage(session, "assistant", orNull(roundText.toString()),
                            toJson(toolCalls), round + 1);
                    saveMessage(session, "tool",
                            truncate(toJson(toolResponseMessage.getResponses().stream()
                                    .map(r -> new ToolResponseMessage.ToolResponse(
                                            r.id(), r.name(), ToolResultUnwrapper.unwrap(r.responseData())))
                                    .toList())),
                            null, round + 1);
                }
                instructions = new ArrayList<>(updatedMessages);
            }

            if (finalAnswer == null) {
                finalAnswer = "已达最大推理轮次（" + maxIterations + "轮），为避免死循环已终止。"
                        + "请尝试拆分问题或更换更明确的描述。";
                saveMessage(session, "assistant", finalAnswer, null, maxIterations);
            } else {
                saveMessage(session, "assistant", finalAnswer, null, round + 1);
            }

            chatService.refreshCounts(session.getId());
            long costMs = System.currentTimeMillis() - startedAt;
            send(emitter, "done", Map.of("answer", finalAnswer, "rounds", round + 1, "costMs", costMs),
                    session.getId(), messageId, round + 1);
            log.info("Agent 完成 sessionId={} agent={} rounds={} cost={}ms",
                    session.getId(), agentType.code(), round + 1, costMs);
        } catch (ClientGoneException e) {
            log.info("客户端断开，终止推理 sessionId={}", session.getId());
        } catch (BizException e) {
            log.warn("Agent 业务异常 sessionId={}: {}", session.getId(), e.getMessage());
            sendQuietly(emitter, "error", Map.of("message", e.getMessage()), session.getId());
            saveMessage(session, "assistant", "抱歉，推理失败：" + e.getMessage(), null, 0);
        } catch (Exception e) {
            log.error("Agent 执行异常 sessionId={}", session.getId(), e);
            sendQuietly(emitter, "error",
                    Map.of("message", "推理过程发生异常：" + e.getClass().getSimpleName()), session.getId());
            saveMessage(session, "assistant", "抱歉，推理过程发生异常，请稍后重试。", null, 0);
        } finally {
            try {
                emitter.complete();
            } catch (Exception ignore) {
                // ignore
            }
        }
    }

    private void handleChunk(org.springframework.ai.chat.model.ChatResponse response,
                             StringBuilder roundText, ToolCallAccumulator accumulator,
                             SseEmitter emitter, Long sessionId, String messageId, int round) {
        if (response == null || response.getResults() == null) {
            return;
        }
        for (Generation generation : response.getResults()) {
            AssistantMessage output = generation.getOutput();
            if (output == null) {
                continue;
            }
            String text = output.getText();
            if (text != null && !text.isEmpty()) {
                roundText.append(text);
                send(emitter, "message", Map.of("delta", text), sessionId, messageId, round);
            }
            if (output.getToolCalls() != null && !output.getToolCalls().isEmpty()) {
                accumulator.merge(output.getToolCalls());
            }
        }
    }

    private void emitToolCalls(SseEmitter emitter, Long sessionId, String messageId, int round,
                               List<AssistantMessage.ToolCall> toolCalls) {
        List<Map<String, Object>> calls = toolCalls.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.id());
            m.put("name", c.name());
            m.put("arguments", c.arguments());
            return m;
        }).toList();
        send(emitter, "tool_call", Map.of("calls", calls), sessionId, messageId, round);
    }

    private void emitToolResults(SseEmitter emitter, Long sessionId, String messageId, int round,
                                 ToolResponseMessage toolResponseMessage) {
        List<Map<String, Object>> results = toolResponseMessage.getResponses().stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.id());
            m.put("name", r.name());
            // 工具返回值可能被默认转换器做过 JSON 字符串包装，解包后再下发与落库
            m.put("responseData", truncate(ToolResultUnwrapper.unwrap(r.responseData())));
            return m;
        }).toList();
        send(emitter, "tool_result", Map.of("results", results), sessionId, messageId, round);
    }

    private void saveMessage(ChatSession session, String role, String content, String toolCallsJson, int round) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(session.getId());
        message.setUserId(session.getUserId());
        message.setRole(role);
        message.setContent(content);
        message.setToolCallsJson(toolCallsJson);
        message.setRound(round);
        persistence.save(message);
    }

    private void send(SseEmitter emitter, String event, Map<String, Object> data,
                      Long sessionId, String messageId, Integer round) {
        try {
            String payload = objectMapper.writeValueAsString(
                    StreamEvent.of(event, data, sessionId, messageId, round));
            emitter.send(SseEmitter.event().name(event).data(payload));
        } catch (Exception e) {
            throw new ClientGoneException(e);
        }
    }

    private void sendQuietly(SseEmitter emitter, String event, Map<String, Object> data, Long sessionId) {
        try {
            String payload = objectMapper.writeValueAsString(StreamEvent.of(event, data, sessionId, null, 0));
            emitter.send(SseEmitter.event().name(event).data(payload));
        } catch (Exception ignore) {
            // client likely gone
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_TOOL_RESULT_LOG ? s : s.substring(0, MAX_TOOL_RESULT_LOG) + "…(截断)";
    }

    private String orNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static final class ClientGoneException extends RuntimeException {
        private ClientGoneException(Throwable cause) {
            super(cause);
        }
    }
}
