package com.lingxi.agent.tools;

import com.lingxi.modules.report.Report;
import com.lingxi.modules.report.ReportService;
import com.lingxi.security.SecurityUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 报告保存工具：Agent 生成的报告落库，供报告中心查看/下载。
 */
@Component
public class ReportTool {

    private final ReportService reportService;

    public ReportTool(ReportService reportService) {
        this.reportService = reportService;
    }

    @Tool(name = "save_report",
            description = "将生成的 Markdown 报告保存到平台报告中心，返回报告 ID。调用前请确保报告内容已完整撰写")
    public String saveReport(
            @ToolParam(description = "报告标题") String title,
            @ToolParam(description = "完整的 Markdown 报告内容") String content) {
        Long userId = null;
        try {
            userId = SecurityUtils.current().userId();
        } catch (Exception ignore) {
            // 无认证上下文（如测试）时允许匿名创建
        }
        Report report = reportService.create(title, content, null, userId);
        return "报告已保存：ID=" + report.getId() + "，《" + report.getTitle() + "》，可在报告中心查看与下载。";
    }
}
