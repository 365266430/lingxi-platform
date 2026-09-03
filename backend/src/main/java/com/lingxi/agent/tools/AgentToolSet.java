package com.lingxi.agent.tools;

import com.lingxi.agent.core.AgentType;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 智能体工具集装配：为每类智能体挑选工具子集并转换为 ToolCallback。
 */
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

    public AgentToolSet(KnowledgeBaseTool knowledgeBaseTool, AlertTool alertTool, MetricsTool metricsTool,
                        CmdbTool cmdbTool, SqlQueryTool sqlQueryTool, DateTimeTool dateTimeTool,
                        WebSearchTool webSearchTool, ReportTool reportTool, NotificationTool notificationTool) {
        this.knowledgeBaseTool = knowledgeBaseTool;
        this.alertTool = alertTool;
        this.metricsTool = metricsTool;
        this.cmdbTool = cmdbTool;
        this.sqlQueryTool = sqlQueryTool;
        this.dateTimeTool = dateTimeTool;
        this.webSearchTool = webSearchTool;
        this.reportTool = reportTool;
        this.notificationTool = notificationTool;
    }

    public List<ToolCallback> forAgent(AgentType type) {
        Map<AgentType, List<Object>> assignments = new EnumMap<>(AgentType.class);
        assignments.put(AgentType.KNOWLEDGE_QA, List.of(knowledgeBaseTool, dateTimeTool));
        assignments.put(AgentType.OPS_DIAGNOSIS,
                List.of(alertTool, metricsTool, cmdbTool, knowledgeBaseTool, dateTimeTool));
        assignments.put(AgentType.DATA_ANALYSIS, List.of(sqlQueryTool, dateTimeTool));
        assignments.put(AgentType.REPORT,
                List.of(knowledgeBaseTool, alertTool, metricsTool, cmdbTool, reportTool, dateTimeTool));
        assignments.put(AgentType.SUPERVISOR,
                List.of(knowledgeBaseTool, alertTool, metricsTool, cmdbTool, sqlQueryTool,
                        webSearchTool, reportTool, notificationTool, dateTimeTool));
        return assignments.getOrDefault(type, assignments.get(AgentType.SUPERVISOR)).stream()
                .flatMap(bean -> List.of(MethodToolCallbackProvider.builder()
                        .toolObjects(bean).build().getToolCallbacks()).stream())
                .toList();
    }
}
