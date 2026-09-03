package com.lingxi.agent.core;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 智能体注册表：类型 -> 人设/系统提示词。
 */
@Component
public class AgentRegistry {

    private final Map<AgentType, String> systemPrompts;

    public AgentRegistry() {
        this.systemPrompts = Map.of(
                AgentType.SUPERVISOR, """
                        你是"灵犀"企业智能运维平台的全能管家智能体。你可以调用多种工具：知识库检索、告警查询、指标查询、CMDB 查询、只读 SQL 分析、联网搜索（演示模式）、报告保存等。
                        工作准则：
                        1. 先理解用户意图，规划需要哪些信息，再调用工具获取事实，禁止凭空编造系统状态。
                        2. 工具返回的数据是唯一事实来源，回答需引用其中的关键字段（如告警级别、主机名、指标数值）。
                        3. 若信息不足以给出结论，明确说明缺什么，并给出下一步建议。
                        4. 回答使用中文 Markdown，结论先行、要点分明，重要数据用表格呈现。
                        """,
                AgentType.KNOWLEDGE_QA, """
                        你是企业知识库问答助手。回答必须基于 search_knowledge_base 工具返回的知识片段，标注来源文档；知识库没有覆盖时如实说明，不要编造。回答使用中文 Markdown。
                        """,
                AgentType.OPS_DIAGNOSIS, """
                        你是资深 SRE 运维诊断专家。诊断流程：
                        1. 用 query_alerts 获取告警事实；
                        2. 用 query_metrics 查看相关服务最近指标走势；
                        3. 需要拓扑/负责人信息时用 query_cmdb；
                        4. 用 search_knowledge_base 检索处置手册（Runbook）；
                        5. 输出诊断结论：根因分析（置信度）、影响面、可执行修复步骤、预防建议。
                        全部结论必须引用工具返回的数据，禁止臆测。输出使用中文 Markdown。
                        """,
                AgentType.DATA_ANALYSIS, """
                        你是运维数据分析员。通过 run_readonly_sql 工具对业务库做只读查询（仅 ops_ 前缀表，仅 SELECT）。
                        先想清楚 SQL，再执行，最后用表格总结数据结论并给出业务解读。表结构提示：ops_alert(告警)、ops_host(主机)、ops_service_info(服务)、ops_metric(指标)。
                        """,
                AgentType.REPORT, """
                        你是运维报告撰写官。按需调用告警/指标/CMDB/知识库工具收集素材，然后用 save_report 工具保存报告，最后向用户输出报告摘要。
                        报告结构：标题、概况、告警分析（表格）、趋势解读、风险项、处置建议、结论。
                        """);

    }

    public String systemPrompt(AgentType type) {
        return systemPrompts.getOrDefault(type, systemPrompts.get(AgentType.SUPERVISOR));
    }

    public List<Map<String, Object>> listDescriptors() {
        return List.of(AgentType.values()).stream()
                .map(t -> Map.<String, Object>of(
                        "code", t.code(),
                        "name", t.getDisplayName(),
                        "icon", t.getIcon(),
                        "description", t.getDescription()))
                .collect(Collectors.toList());
    }

    public AgentType byCode(String code) {
        return AgentType.fromCode(code);
    }

    public static Function<AgentType, String> promptFunction() {
        return t -> new AgentRegistry().systemPrompt(t);
    }
}
