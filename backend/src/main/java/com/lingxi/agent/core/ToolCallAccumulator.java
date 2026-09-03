package com.lingxi.agent.core;

import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流式工具调用累加器。
 * <p>
 * OpenAI 流式协议中，一次工具调用会被拆成多个 delta：首片携带 id/name，
 * 后续片只携带参数增量。本累加器按"出现身份（id 或 name）即开启新分片，
 * 无身份分片追加到最近分片"的策略跨 chunk 合并参数 JSON。
 */
public class ToolCallAccumulator {

    private final Map<String, Partial> partials = new LinkedHashMap<>();
    private String lastKey;

    public void merge(List<AssistantMessage.ToolCall> fragments) {
        if (fragments == null) {
            return;
        }
        for (AssistantMessage.ToolCall fragment : fragments) {
            boolean hasId = fragment.id() != null && !fragment.id().isBlank();
            boolean hasName = fragment.name() != null && !fragment.name().isBlank();
            if (hasId || hasName) {
                String key = hasId ? fragment.id() : fragment.name();
                Partial partial = partials.computeIfAbsent(key,
                        k -> new Partial(fragment.id(), "function", fragment.name(), new StringBuilder()));
                if (hasId) {
                    partial.id = fragment.id();
                }
                if (hasName) {
                    partial.name = fragment.name();
                }
                if (fragment.type() != null && !fragment.type().isBlank()) {
                    partial.type = fragment.type();
                }
                appendArgs(partial, fragment.arguments());
                lastKey = key;
            } else if (lastKey != null) {
                appendArgs(partials.get(lastKey), fragment.arguments());
            }
        }
    }

    private void appendArgs(Partial partial, String arguments) {
        if (partial != null && arguments != null) {
            partial.arguments.append(arguments);
        }
    }

    public boolean isEmpty() {
        return partials.isEmpty();
    }

    public List<AssistantMessage.ToolCall> complete() {
        List<AssistantMessage.ToolCall> result = new ArrayList<>();
        for (Partial p : partials.values()) {
            result.add(new AssistantMessage.ToolCall(p.id, p.type, p.name, p.arguments.toString()));
        }
        return result;
    }

    private static final class Partial {
        private String id;
        private String type;
        private String name;
        private final StringBuilder arguments;

        private Partial(String id, String type, String name, StringBuilder arguments) {
            this.id = id;
            this.type = type;
            this.name = name;
            this.arguments = arguments;
        }
    }
}
