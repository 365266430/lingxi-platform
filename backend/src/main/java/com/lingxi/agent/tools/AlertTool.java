package com.lingxi.agent.tools;

import com.lingxi.modules.ops.OpsQueryService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 告警查询工具。
 */
@Component
public class AlertTool {

    private final OpsQueryService opsQueryService;

    public AlertTool(OpsQueryService opsQueryService) {
        this.opsQueryService = opsQueryService;
    }

    @Tool(name = "query_alerts",
            description = "查询平台当前活跃告警列表，可按级别与关键字过滤，返回 Markdown 表格（含 ID、级别、服务、主机、指标、开始时间）")
    public String queryAlerts(
            @ToolParam(required = false, description = "告警级别过滤：P1、P2 或 P3，留空查全部") String severity,
            @ToolParam(required = false, description = "关键字，匹配服务名或告警标题，留空查全部") String keyword) {
        return opsQueryService.activeAlertsMarkdown(blankToNull(severity), blankToNull(keyword));
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
