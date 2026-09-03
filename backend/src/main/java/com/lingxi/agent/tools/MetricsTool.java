package com.lingxi.agent.tools;

import com.lingxi.modules.ops.OpsQueryService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 指标查询工具。
 */
@Component
public class MetricsTool {

    private final OpsQueryService opsQueryService;

    public MetricsTool(OpsQueryService opsQueryService) {
        this.opsQueryService = opsQueryService;
    }

    @Tool(name = "query_metrics",
            description = "查询服务最近一段时间的 CPU/内存指标概况（最新值、均值、峰值），返回 Markdown 表格")
    public String queryMetrics(
            @ToolParam(required = false, description = "服务名称，留空则汇总全部服务") String serviceName,
            @ToolParam(required = false, description = "回溯小时数，默认 6，最大 168") Integer hours) {
        int window = hours == null || hours <= 0 ? 6 : Math.min(hours, 168);
        return opsQueryService.metricsMarkdown(serviceName, window);
    }
}
