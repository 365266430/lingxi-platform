package com.lingxi.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 流式工具调用累加器测试（模拟 OpenAI 分片协议）。
 */
class ToolCallAccumulatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("首片携带 id/name，后续片仅参数增量，最终合并出完整参数 JSON")
    void mergeFragmentedArguments() throws Exception {
        ToolCallAccumulator accumulator = new ToolCallAccumulator();
        accumulator.merge(List.of(new AssistantMessage.ToolCall(
                "call_abc", "function", "search_knowledge_base", "{\"query\": \"磁盘")));
        accumulator.merge(List.of(new AssistantMessage.ToolCall(
                null, null, null, "空间不足的处置")));
        accumulator.merge(List.of(new AssistantMessage.ToolCall(
                null, null, null, "步骤\"}")));

        List<AssistantMessage.ToolCall> calls = accumulator.complete();
        assertThat(calls).hasSize(1);
        AssistantMessage.ToolCall call = calls.get(0);
        assertThat(call.id()).isEqualTo("call_abc");
        assertThat(call.name()).isEqualTo("search_knowledge_base");
        var parsed = objectMapper.readTree(call.arguments());
        assertThat(parsed.get("query").asText()).isEqualTo("磁盘空间不足的处置步骤");
    }

    @Test
    @DisplayName("多个工具调用按 id 独立合并（模拟流式顺序：同一调用的分片连续到达）")
    void mergeMultipleCalls() {
        ToolCallAccumulator accumulator = new ToolCallAccumulator();
        accumulator.merge(List.of(new AssistantMessage.ToolCall("c1", "function", "query_alerts", "{\"")));
        accumulator.merge(List.of(new AssistantMessage.ToolCall(null, null, null, "severity\":\"P1\"}")));
        accumulator.merge(List.of(new AssistantMessage.ToolCall("c2", "function", "get_current_time", "{}")));

        List<AssistantMessage.ToolCall> calls = accumulator.complete();
        assertThat(calls).hasSize(2);
        assertThat(calls).extracting(AssistantMessage.ToolCall::name)
                .containsExactlyInAnyOrder("query_alerts", "get_current_time");
        assertThat(calls.stream().filter(c -> "query_alerts".equals(c.name())).findFirst().orElseThrow()
                .arguments()).isEqualTo("{\"severity\":\"P1\"}");
    }

    @Test
    @DisplayName("无分片时结果为空")
    void emptyWhenNoFragments() {
        ToolCallAccumulator accumulator = new ToolCallAccumulator();
        assertThat(accumulator.isEmpty()).isTrue();
        assertThat(accumulator.complete()).isEmpty();
    }
}
