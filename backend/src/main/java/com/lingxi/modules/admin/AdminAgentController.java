package com.lingxi.modules.admin;

import com.lingxi.agent.core.AgentDefinition;
import com.lingxi.agent.core.AgentDefinitionService;
import com.lingxi.common.api.Result;
import com.lingxi.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Agent 定义管理，仅管理员可访问（由 SecurityConfig 统一保护 /api/admin/**）。 */
@Tag(name = "系统管理-Agent")
@RestController
@RequestMapping("/api/admin/agents")
@RequiredArgsConstructor
public class AdminAgentController {

    private final AgentDefinitionService service;

    @Operation(summary = "Agent 列表")
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(service.listForAdmin().stream().map(this::view).toList());
    }

    @Operation(summary = "新增 Agent")
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody AgentDefinition definition) {
        return Result.ok(view(service.create(definition, SecurityUtils.currentUserId())));
    }

    @Operation(summary = "编辑 Agent")
    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable Long id, @RequestBody AgentDefinition definition) {
        return Result.ok(view(service.update(id, definition, SecurityUtils.currentUserId())));
    }

    @Operation(summary = "启用/停用 Agent")
    @PutMapping("/{id}/status")
    public Result<Void> status(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        service.updateStatus(id, body.get("enabled"), SecurityUtils.currentUserId());
        return Result.ok();
    }

    @Operation(summary = "删除自定义 Agent")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    private Map<String, Object> view(AgentDefinition definition) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", definition.getId());
        item.put("code", definition.getCode());
        item.put("name", definition.getName());
        item.put("icon", definition.getIcon());
        item.put("description", definition.getDescription());
        item.put("systemPrompt", definition.getSystemPrompt());
        item.put("tools", service.toolNames(definition));
        item.put("enabled", definition.getEnabled());
        item.put("builtin", definition.getBuiltin());
        item.put("sort", definition.getSort());
        item.put("updatedAt", definition.getUpdatedAt());
        return item;
    }
}
