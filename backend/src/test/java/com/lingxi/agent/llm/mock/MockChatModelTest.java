package com.lingxi.agent.llm.mock;

import com.lingxi.agent.core.ToolCallAccumulator;
import com.lingxi.agent.tools.KnowledgeBaseTool;
import com.lingxi.rag.service.RetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mock 模型行为测试：剧本流转、流式工具调用分片可被累加器正确合并。
 */
class MockChatModelTest {

    private MockChatModel mockChatModel;
    private ToolCallback knowledgeBaseCallback;

    @BeforeEach
    void setUp() {
        mockChatModel = new MockChatModel();
        RetrievalService retrievalService = mock(RetrievalService.class);
        when(retrievalService.retrieveMarkdown(any(), anyString(), anyInt()))
                .thenReturn("**[1] 磁盘手册**\n清理步骤…");
        KnowledgeBaseTool tool = new KnowledgeBaseTool(retrievalService);
        knowledgeBaseCallback = MethodToolCallbackProvider.builder().toolObjects(tool).build()
                .getToolCallbacks()[0];
    }

    private ToolCallingChatOptions optionsWithKbTool() {
        return ToolCallingChatOptions.builder()
                .toolCallbacks(List.of(knowledgeBaseCallback))
                .internalToolExecutionEnabled(false)
                .build();
    }

    @Test
    @DisplayName("问候语直接产出文本（不调用工具）")
    void greetingProducesText() {
        ChatResponse response = mockChatModel.call(new Prompt(List.of(new UserMessage("你好")), optionsWithKbTool()));
        String text = response.getResult().getOutput().getText();
        assertThat(text).contains("灵犀");
        assertThat(response.getResult().getOutput().getToolCalls()).isEmpty();
    }

    @Test
    @DisplayName("运维问题第一轮发起知识库工具调用（带可用工具时）")
    void opsQuestionTriggersToolCall() {
        Prompt prompt = new Prompt(List.of(new UserMessage("线上磁盘空间告警如何处置？")), optionsWithKbTool());
        ChatResponse response = mockChatModel.call(prompt);
        List<AssistantMessage.ToolCall> calls = response.getResult().getOutput().getToolCalls();
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).name()).isEqualTo("search_knowledge_base");
        assertThat(calls.get(0).arguments()).contains("query");
    }

    @Test
    @DisplayName("工具应答之后（第二轮）产出包含工具数据的最终回答")
    void finalAnswerAfterToolResponse() {
        Prompt prompt = new Prompt(
                List.of(
                        new UserMessage("知识库 什么是 Runbook？"),
                        new AssistantMessage("", java.util.Map.of(), List.of(new AssistantMessage.ToolCall(
                                "call_1", "function", "search_knowledge_base", "{\"query\":\"Runbook\"}"))),
                        new ToolResponseMessage(List.of(new ToolResponseMessage.ToolResponse(
                                "call_1", "search_knowledge_base", "Runbook 即标准化处置手册，包含排查步骤与升级条件…")))),
                optionsWithKbTool());
        ChatResponse response = mockChatModel.call(prompt);
        String text = response.getResult().getOutput().getText();
        assertThat(text).contains("Runbook").contains("处置手册");
        assertThat(response.getResult().getOutput().getToolCalls()).isEmpty();
    }

    @Test
    @DisplayName("流式输出的分片工具调用可被 ToolCallAccumulator 完整重组")
    void streamToolCallFragmentsAreMergeable() {
        Prompt prompt = new Prompt(List.of(new UserMessage("线上磁盘告警，帮我看看")), optionsWithKbTool());
        Flux<ChatResponse> flux = mockChatModel.stream(prompt);

        StringBuilder text = new StringBuilder();
        ToolCallAccumulator accumulator = new ToolCallAccumulator();
        List<ChatResponse> chunks = flux.collectList().block();
        assertThat(chunks).isNotNull();
        for (ChatResponse chunk : chunks) {
            AssistantMessage output = chunk.getResult().getOutput();
            if (output.getText() != null && !output.getText().isEmpty()) {
                text.append(output.getText());
            }
            if (output.getToolCalls() != null && !output.getToolCalls().isEmpty()) {
                accumulator.merge(output.getToolCalls());
            }
        }
        List<AssistantMessage.ToolCall> calls = accumulator.complete();
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).name()).isEqualTo("search_knowledge_base");
        assertThat(calls.get(0).arguments()).startsWith("{").endsWith("}");
    }

    @Test
    @DisplayName("无可用工具时输出兜底说明")
    void plainAnswerWithoutTools() {
        ChatResponse response = mockChatModel.call(new Prompt(List.of(new UserMessage("随机问题"))));
        assertThat(response.getResult().getOutput().getText()).contains("演示模式");
    }

    @Test
    @DisplayName("最终文本流式分片拼接后与整体一致")
    void streamTextConcatenates() {
        Prompt prompt = new Prompt(List.of(new UserMessage("你好")));
        List<ChatResponse> chunks = mockChatModel.stream(prompt).collectList().block();
        StringBuilder sb = new StringBuilder();
        for (ChatResponse chunk : chunks) {
            sb.append(chunk.getResult().getOutput().getText());
        }
        assertThat(sb.toString()).contains("灵犀智能体平台");
    }

    @Test
    @DisplayName("多轮工具编排：OPS 场景三轮工具后收敛为最终诊断")
    void opsScenarioConverges() {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new UserMessage("订单服务 CPU 告警，帮我诊断一下"));
        ToolCallingChatOptions options = optionsWithKbTool();
        String finalText = null;
        for (int round = 0; round < 6; round++) {
            ChatResponse response = mockChatModel.call(new Prompt(messages, options));
            List<AssistantMessage.ToolCall> calls = response.getResult().getOutput().getToolCalls();
            if (calls == null || calls.isEmpty()) {
                finalText = response.getResult().getOutput().getText();
                break;
            }
            messages.add(new AssistantMessage("", java.util.Map.of(), calls));
            List<ToolResponseMessage.ToolResponse> responses = calls.stream()
                    .map(c -> new ToolResponseMessage.ToolResponse(c.id(), c.name(),
                            "（工具数据：id=" + c.name() + "）诊断所需的事实数据"))
                    .toList();
            messages.add(new ToolResponseMessage(responses));
        }
        assertThat(finalText).isNotNull();
        assertThat(finalText).contains("诊断").contains("告警事实");
    }
}
