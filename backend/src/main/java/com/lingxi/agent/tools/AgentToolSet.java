package com.lingxi.agent.tools;

import com.lingxi.agent.core.AgentDefinition;
import com.lingxi.agent.core.AgentDefinitionService;
import com.lingxi.agent.core.AgentType;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 智能体工具集装配：工具权限来自 Agent 管理配置。 */
@Component
public class AgentToolSet {

    private final KnowledgeBaseTool knowledgeBaseTool;
    private final AlertTool alertTool;
    private final MetricsTool metricsTool;
    private final CmdbTool cmdbTool;
    private final SqlQueryTool sqlQueryTool;
    private final DateTimeTool dateTimeTool;
    private final WebSearchTool webSearchTool;
    private final ReportTool reportTool;
    private final NotificationTool notificationTool;
    private final AgentDefinitionService definitionService;
    private final Map<String, ToolCallback> callbacks = new HashMap<>();

    public AgentToolSet(KnowledgeBaseTool knowledgeBaseTool, AlertTool alertTool, MetricsTool metricsTool,
                        CmdbTool cmdbTool, SqlQueryTool sqlQueryTool, DateTimeTool dateTimeTool,
                        WebSearchTool webSearchTool, ReportTool reportTool, NotificationTool notificationTool,
                        AgentDefinitionService definitionService) {
        this.knowledgeBaseTool = knowledgeBaseTool;
        this.alertTool = alertTool;
        this.metricsTool = metricsTool;
        this.cmdbTool = cmdbTool;
        this.sqlQueryTool = sqlQueryTool;
        this.dateTimeTool = dateTimeTool;
        this.webSearchTool = webSearchTool;
        this.reportTool = reportTool;
        this.notificationTool = notificationTool;
        this.definitionService = definitionService;
        register(knowledgeBaseTool);
        register(alertTool);
        register(metricsTool);
        register(cmdbTool);
        register(sqlQueryTool);
        register(dateTimeTool);
        register(webSearchTool);
        register(reportTool);
        register(notificationTool);
    }

    public List<ToolCallback> forAgent(AgentType type) {
        return forAgent(type.code());
    }

    public List<ToolCallback> forAgent(String code) {
        AgentDefinition definition = definitionService.getByCode(code);
        return definitionService.toolNames(definition).stream()
                .map(callbacks::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private void register(Object bean) {
        for (ToolCallback callback : MethodToolCallbackProvider.builder().toolObjects(bean).build().getToolCallbacks()) {
            callbacks.put(callback.getToolDefinition().name(), callback);
        }
    }
}
