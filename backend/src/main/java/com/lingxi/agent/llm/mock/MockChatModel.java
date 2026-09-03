package com.lingxi.agent.llm.mock;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 离线脚本化 ChatModel：模拟"多轮工具调用 + 流式输出"的真实模型行为，
 * 是本平台可测试、可演示的关键设计（详见设计报告 6.4 节）。
 */
public class MockChatModel implements ChatModel {

    public static final String MODEL_ID = "lingxi-mock-1";

    private final ScriptLibrary scripts = new ScriptLibrary();

    @Override
    public ChatResponse call(Prompt prompt) {
        ScriptLibrary.Decision decision = decide(prompt);
        Generation generation = decision.call() != null
                ? new Generation(new AssistantMessage("", Map.of(),
                        List.of(new AssistantMessage.ToolCall(callId(), "function",
                                decision.call().tool(), decision.call().arguments()))))
                : new Generation(new AssistantMessage(decision.finalText()));
        return new ChatResponse(List.of(generation), buildMetadata());
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        ScriptLibrary.Decision decision = decide(prompt);
        if (decision.call() != null) {
            String id = callId();
            String args = decision.call().arguments();
            int len = args.length();
            int cut1 = Math.max(1, len / 3);
            int cut2 = Math.max(cut1 + 1, len * 2 / 3);
            // 首片带 id/name，后续片只有参数增量 —— 模拟真实 OpenAI 流式工具调用
            return Flux.just(
                    toolChunk(List.of(new AssistantMessage.ToolCall(id, "function",
                            decision.call().tool(), args.substring(0, cut1)))),
                    toolChunk(List.of(new AssistantMessage.ToolCall(null, null, null,
                            args.substring(cut1, cut2)))),
                    toolChunk(List.of(new AssistantMessage.ToolCall(null, null, null,
                            args.substring(cut2)))));
        }
        String text = decision.finalText();
        List<ChatResponse> chunks = new ArrayList<>();
        for (String part : split(text, 64)) {
            chunks.add(textChunk(new AssistantMessage(part)));
        }
        return Flux.fromIterable(chunks).delayElements(Duration.ofMillis(15));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return new MockChatOptions();
    }

    private ScriptLibrary.Decision decide(Prompt prompt) {
        List<Message> messages = prompt.getInstructions();
        int lastUserIndex = -1;
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof UserMessage) {
                lastUserIndex = i;
            }
        }
        String userText = lastUserIndex >= 0 && messages.get(lastUserIndex) instanceof UserMessage um
                ? um.getText() : "";
        int round = 0;
        List<ToolResponseMessage.ToolResponse> responses = List.of();
        for (int i = lastUserIndex + 1; i < messages.size(); i++) {
            if (messages.get(i) instanceof ToolResponseMessage trm) {
                round++;
                responses = trm.getResponses();
            }
        }
        return scripts.decide(userText, round, availableTools(prompt), responses);
    }

    private List<String> availableTools(Prompt prompt) {
        if (prompt.getOptions() instanceof ToolCallingChatOptions toolOptions
                && toolOptions.getToolCallbacks() != null) {
            return toolOptions.getToolCallbacks().stream()
                    .map(cb -> cb.getToolDefinition().name())
                    .toList();
        }
        return List.of();
    }

    private ChatResponse toolChunk(List<AssistantMessage.ToolCall> toolCalls) {
        AssistantMessage message = toolCalls == null || toolCalls.isEmpty()
                ? new AssistantMessage("")
                : new AssistantMessage("", Map.of(), toolCalls);
        return new ChatResponse(List.of(new Generation(message)), buildMetadata());
    }

    private ChatResponse textChunk(AssistantMessage message) {
        return new ChatResponse(List.of(new Generation(message)), buildMetadata());
    }

    private org.springframework.ai.chat.metadata.ChatResponseMetadata buildMetadata() {
        return org.springframework.ai.chat.metadata.ChatResponseMetadata.builder()
                .model(MODEL_ID)
                .usage(new MockUsage())
                .build();
    }

    private static final class MockUsage implements org.springframework.ai.chat.metadata.Usage {
        @Override
        public Integer getPromptTokens() {
            return 0;
        }

        @Override
        public Integer getCompletionTokens() {
            return 0;
        }

        @Override
        public Object getNativeUsage() {
            return Map.of();
        }
    }

    private String callId() {
        return "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private List<String> split(String text, int size) {
        List<String> parts = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            parts.add("");
            return parts;
        }
        for (int i = 0; i < text.length(); i += size) {
            parts.add(text.substring(i, Math.min(text.length(), i + size)));
        }
        return parts;
    }
}
