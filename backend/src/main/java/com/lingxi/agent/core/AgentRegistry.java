package com.lingxi.agent.core;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** 智能体注册表：管理端定义 -> 运行时提示词与展示信息。 */
@Component
@RequiredArgsConstructor
public class AgentRegistry {

    private final AgentDefinitionService definitions;

    public String systemPrompt(String code) {
        return definitions.getByCode(code).getSystemPrompt();
    }

    public String systemPrompt(AgentType type) {
        return systemPrompt(type.code());
    }

    public AgentDefinition definition(String code) {
        return definitions.getByCode(code);
    }

    public List<Map<String, Object>> listDescriptors() {
        return definitions.listEnabled().stream().map(definition -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", definition.getCode());
            item.put("name", definition.getName());
            item.put("icon", definition.getIcon());
            item.put("description", definition.getDescription());
            item.put("tools", definitions.toolNames(definition));
            return item;
        }).toList();
    }

    public AgentType byCode(String code) {
        return AgentType.fromCode(code);
    }

    public static Function<AgentType, String> promptFunction() {
        return t -> AgentDefinitionDefaults.all().stream()
                .filter(d -> d.code().equals(t.code()))
                .map(AgentDefinitionDefaults.Default::systemPrompt)
                .findFirst().orElse("");
    }
}
