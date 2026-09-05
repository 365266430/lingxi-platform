package com.lingxi.agent.core;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Agent 定义查询、校验与管理。 */
@Service
@RequiredArgsConstructor
public class AgentDefinitionService {

    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");
    private static final Set<String> ALLOWED_TOOLS = Set.of(
            "search_knowledge_base", "query_alerts", "query_metrics", "query_cmdb", "run_readonly_sql",
            "web_search", "save_report", "send_notification", "get_current_time");

    private final AgentDefinitionMapper mapper;
    private final ObjectMapper objectMapper;

    public List<AgentDefinition> listEnabled() {
        List<AgentDefinition> rows = mapper.selectList(new LambdaQueryWrapper<AgentDefinition>()
                .eq(AgentDefinition::getEnabled, 1)
                .orderByAsc(AgentDefinition::getSort)
                .orderByAsc(AgentDefinition::getId));
        return rows.isEmpty() && mapper.selectCount(null) == 0 ? defaults() : rows;
    }

    @Transactional
    public List<AgentDefinition> listForAdmin() {
        ensureDefaults();
        return mapper.selectList(new LambdaQueryWrapper<AgentDefinition>()
                .orderByAsc(AgentDefinition::getSort)
                .orderByAsc(AgentDefinition::getId));
    }

    public AgentDefinition getByCode(String code) {
        String normalized = normalizeCode(code);
        AgentDefinition row = mapper.selectOne(new LambdaQueryWrapper<AgentDefinition>()
                .eq(AgentDefinition::getCode, normalized));
        if (row != null) {
            return row;
        }
        return defaults().stream().filter(d -> d.getCode().equals(normalized)).findFirst()
                .orElseThrow(() -> new BizException("Agent 不存在：" + normalized));
    }

    public String requireRunnable(String code) {
        AgentDefinition definition = getByCode(code == null || code.isBlank() ? "supervisor" : code);
        if (!Integer.valueOf(1).equals(definition.getEnabled())) {
            throw new BizException("Agent 已停用：" + definition.getName());
        }
        return definition.getCode();
    }

    @Transactional
    public AgentDefinition create(AgentDefinition incoming, Long operatorId) {
        validate(incoming, true);
        if (mapper.selectCount(new LambdaQueryWrapper<AgentDefinition>().eq(AgentDefinition::getCode, incoming.getCode())) > 0) {
            throw new BizException("Agent 编码已存在：" + incoming.getCode());
        }
        incoming.setId(null);
        incoming.setBuiltin(0);
        incoming.setEnabled(incoming.getEnabled() == null ? 1 : incoming.getEnabled());
        incoming.setSort(incoming.getSort() == null ? 99 : incoming.getSort());
        incoming.setCreatedBy(operatorId);
        incoming.setUpdatedBy(operatorId);
        mapper.insert(incoming);
        return incoming;
    }

    @Transactional
    public AgentDefinition update(Long id, AgentDefinition incoming, Long operatorId) {
        AgentDefinition existing = mapper.selectById(id);
        if (existing == null) {
            throw BizException.notFound("Agent");
        }
        incoming.setCode(existing.getCode());
        validate(incoming, false);
        existing.setName(incoming.getName());
        existing.setIcon(incoming.getIcon());
        existing.setDescription(incoming.getDescription());
        existing.setSystemPrompt(incoming.getSystemPrompt());
        existing.setToolsJson(incoming.getToolsJson());
        existing.setEnabled(incoming.getEnabled() == null ? existing.getEnabled() : incoming.getEnabled());
        existing.setSort(incoming.getSort() == null ? existing.getSort() : incoming.getSort());
        existing.setUpdatedBy(operatorId);
        mapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void updateStatus(Long id, Integer enabled, Long operatorId) {
        AgentDefinition existing = mapper.selectById(id);
        if (existing == null) {
            throw BizException.notFound("Agent");
        }
        if (enabled == null || (enabled != 0 && enabled != 1)) {
            throw new BizException("状态仅支持 0 或 1");
        }
        AgentDefinition update = new AgentDefinition();
        update.setId(id);
        update.setEnabled(enabled);
        update.setUpdatedBy(operatorId);
        mapper.updateById(update);
    }

    @Transactional
    public void delete(Long id) {
        AgentDefinition existing = mapper.selectById(id);
        if (existing == null) {
            throw BizException.notFound("Agent");
        }
        if (Integer.valueOf(1).equals(existing.getBuiltin())) {
            throw new BizException("内置 Agent 不允许删除，可停用或编辑");
        }
        mapper.deleteById(id);
    }

    public List<String> toolNames(AgentDefinition definition) {
        try {
            List<String> names = objectMapper.readValue(definition.getToolsJson(), new TypeReference<>() {
            });
            return names == null ? List.of() : names;
        } catch (Exception e) {
            throw new BizException("Agent 工具配置格式错误：" + definition.getCode());
        }
    }

    public Set<String> allowedTools() {
        return ALLOWED_TOOLS;
    }

    private void validate(AgentDefinition incoming, boolean creating) {
        if (incoming == null || incoming.getName() == null || incoming.getName().isBlank()) {
            throw new BizException("Agent 名称不能为空");
        }
        if (creating) {
            incoming.setCode(normalizeCode(incoming.getCode()));
        }
        if (incoming.getSystemPrompt() == null || incoming.getSystemPrompt().isBlank()) {
            throw new BizException("系统提示词不能为空");
        }
        List<String> tools = toolNames(incoming);
        if (tools.isEmpty()) {
            throw new BizException("至少配置一个工具");
        }
        if (!ALLOWED_TOOLS.containsAll(tools)) {
            throw new BizException("存在不支持的工具，仅允许：" + String.join(", ", ALLOWED_TOOLS));
        }
        try {
            incoming.setToolsJson(objectMapper.writeValueAsString(new ArrayList<>(tools.stream().distinct().toList())));
        } catch (Exception e) {
            throw new BizException("工具配置无法保存");
        }
    }

    private String normalizeCode(String code) {
        String normalized = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) {
            throw new BizException("Agent 编码需使用 2-64 位小写字母、数字或下划线，且以字母开头");
        }
        return normalized;
    }

    private List<AgentDefinition> defaults() {
        return AgentDefinitionDefaults.all().stream().map(d -> {
            AgentDefinition row = new AgentDefinition();
            row.setCode(d.code());
            row.setName(d.name());
            row.setIcon(d.icon());
            row.setDescription(d.description());
            row.setSystemPrompt(d.systemPrompt());
            try {
                row.setToolsJson(objectMapper.writeValueAsString(d.tools()));
            } catch (Exception e) {
                row.setToolsJson("[]");
            }
            row.setEnabled(1);
            row.setBuiltin(1);
            row.setSort(d.sort());
            return row;
        }).toList();
    }

    private void ensureDefaults() {
        if (mapper.selectCount(null) > 0) {
            return;
        }
        defaults().forEach(mapper::insert);
    }
}
