package com.lingxi.agent.core;

import lombok.Getter;

/**
 * 智能体类型。SUPERVISOR 拥有全部工具；专家智能体拥有各自领域工具子集。
 */
@Getter
public enum AgentType {

    SUPERVISOR("全能管家", "🧭", "总控智能体，可调度全部工具，处理综合问题并自动路由领域子任务"),
    KNOWLEDGE_QA("知识问答", "📚", "基于知识库的 RAG 问答，适合查文档、查手册、查规范"),
    OPS_DIAGNOSIS("运维诊断", "🩺", "面向告警与故障的诊断专家，可查 CMDB、指标、告警并检索处置手册"),
    DATA_ANALYSIS("数据分析", "📊", "使用只读 SQL 对运维业务数据做统计与分析"),
    REPORT("报告撰写", "📝", "汇总多源信息生成结构化巡检/诊断报告");

    private final String displayName;
    private final String icon;
    private final String description;

    AgentType(String displayName, String icon, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String code() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static AgentType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return SUPERVISOR;
        }
        return AgentType.valueOf(code.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
