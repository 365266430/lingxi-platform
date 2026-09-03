package com.lingxi.agent.llm.mock;

import java.util.List;

/**
 * 离线演示剧本库：决定 Mock 模型在每一轮"调用哪个工具 / 何时产出最终回答"。
 * 让整个 Agent 平台在没有真实大模型 API Key 时也能全链路演示与自动化测试。
 */
public class ScriptLibrary {

    public record PlannedCall(String tool, String arguments) {
    }

    public record Decision(PlannedCall call, String finalText) {
        public static Decision callTool(PlannedCall call) {
            return new Decision(call, null);
        }

        public static Decision finish(String text) {
            return new Decision(null, text);
        }
    }

    public enum Scenario { OPS, DATA, REPORT, KB, CHAT }

    /**
     * @param round   本条用户消息之后的工具应答轮数（0 = 模型首次响应）
     * @param tools   本轮可用的工具名
     */
    public Decision decide(String userText, int round, List<String> tools,
                           List<org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse> responses) {
        String text = userText == null ? "" : userText.trim();

        if (isGreeting(text)) {
            return Decision.finish(greetingAnswer());
        }
        if (tools.isEmpty()) {
            return Decision.finish(plainAnswer(text));
        }

        Scenario scenario = detect(text);
        List<PlannedCall> steps = stepsFor(scenario, text);

        if (round < steps.size()) {
            PlannedCall planned = steps.get(round);
            if (tools.contains(planned.tool())) {
                return Decision.callTool(planned);
            }
            // 专家智能体缺该工具时降级为知识库检索
            if (tools.contains("search_knowledge_base")) {
                return Decision.callTool(new PlannedCall("search_knowledge_base",
                        "{\"query\": \"" + escape(jsonKeyword(text)) + "\"}"));
            }
            return Decision.finish(plainAnswer(text));
        }
        return Decision.finish(finalAnswerFor(scenario, text, responses));
    }

    public Scenario detect(String text) {
        if (text.matches(".*(报告|巡检|周报|日报|总结报告).*")) {
            return Scenario.REPORT;
        }
        if (text.matches(".*(统计|分析|多少|数量|排行|TOP|Top|top|占比|分布).*")) {
            return Scenario.DATA;
        }
        if (text.matches(".*(告警|诊断|故障|宕机|排查|CPU|cpu|磁盘|内存|连接池|5xx|异常|错误|起火|掉线).*")) {
            return Scenario.OPS;
        }
        if (text.matches(".*(知识|文档|手册|怎么|如何|什么是|规范|流程|runbook|Runbook).*")) {
            return Scenario.KB;
        }
        return Scenario.CHAT;
    }

    private List<PlannedCall> stepsFor(Scenario scenario, String userText) {
        return switch (scenario) {
            case OPS -> List.of(
                    new PlannedCall("query_alerts", "{\"severity\": \"\", \"keyword\": \"\"}"),
                    new PlannedCall("query_metrics", "{\"serviceName\": \"\", \"hours\": 6}"),
                    new PlannedCall("search_knowledge_base",
                            "{\"query\": \"" + escape(jsonKeyword(userText)) + "\"}"));
            case DATA -> List.of(new PlannedCall("run_readonly_sql",
                    "{\"sql\": \"SELECT service_name AS \\u670d\\u52a1, COUNT(*) AS \\u544a\\u8b66\\u6570 FROM ops_alert WHERE status = 'ACTIVE' GROUP BY service_name ORDER BY \\u544a\\u8b66\\u6570 DESC\"}"));
            case REPORT -> List.of(
                    new PlannedCall("query_alerts", "{\"severity\": \"\", \"keyword\": \"\"}"),
                    new PlannedCall("search_knowledge_base", "{\"query\": \"巡检 处置 手册\"}"),
                    new PlannedCall("save_report",
                            "{\"title\": \"灵犀平台例行巡检报告\", \"content\": \"# 灵犀平台例行巡检报告\\n\\n## 一、告警概况\\n（见工具返回数据）\\n\\n## 二、风险项\\n- 待根据告警数据补充\\n\\n## 三、处置建议\\n- 参照知识库处置手册执行\\n\\n## 四、结论\\n平台整体可控，重点告警已进入诊断流程。\"}"));
            case KB -> List.of(new PlannedCall("search_knowledge_base",
                    "{\"query\": \"" + escape(jsonKeyword(userText)) + "\"}"));
            case CHAT -> List.of();
        };
    }

    private String finalAnswerFor(Scenario scenario, String userText,
                                  List<org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse> responses) {
        String alerts = findResponse(responses, "query_alerts");
        String metrics = findResponse(responses, "query_metrics");
        String kb = findResponse(responses, "search_knowledge_base");
        String sql = findResponse(responses, "run_readonly_sql");
        String report = findResponse(responses, "save_report");

        return switch (scenario) {
            case OPS -> """
                    ### 诊断结论

                    已完成"告警确认 → 指标观察 → 手册匹配"三步诊断，结论如下：

                    **1. 告警事实**

                    %s

                    **2. 指标观察**

                    %s

                    **3. 处置建议（来源：知识库 Runbook）**

                    %s

                    **总结**：请优先处理 P1/P2 级告警，按上述手册步骤执行；若 30 分钟内指标未回落，建议升级至值班主管并考虑服务降级。
                    """.formatted(squeeze(alerts), squeeze(metrics), squeeze(kb));
            case DATA -> """
                    ### 数据分析结果

                    已对运维业务库执行只读查询，结果如下：

                    %s

                    **解读**：以上为当前活跃告警的服务维度分布，数值越高的服务风险越集中，建议结合变更记录排查高频告警服务的根因。
                    """.formatted(squeeze(sql));
            case REPORT -> """
                    巡检报告已生成并保存到平台（可通过左侧"报告中心"查看与下载）。

                    %s

                    报告包含告警概况、风险项与处置建议三个部分，知识库素材已自动引用。
                    """.formatted(squeeze(report));
            case KB -> """
                    根据知识库检索结果，回答如下：

                    %s

                    以上内容引用自知识库匹配到的文档片段；如需更完整的上下文，可在"知识中心"查看原文档。
                    """.formatted(squeeze(kb));
            case CHAT -> plainAnswer(userText);
        };
    }

    private boolean isGreeting(String text) {
        return text.length() <= 12 && text.matches("(?i)^(你好|您好|hi|hello|在吗|嗨|早上好|晚上好)[!！。.~\\s]*$");
    }

    private String greetingAnswer() {
        return """
                你好，我是**灵犀智能体平台**的 AI 助手 🤖

                我可以帮你：
                - 🩺 **故障诊断**：如"线上订单服务 CPU 告警，帮我诊断一下"
                - 📚 **知识问答**：如"磁盘空间不足的处置手册是什么"
                - 📊 **数据分析**：如"统计一下各服务当前活跃告警数量"
                - 📝 **巡检报告**：如"帮我生成一份今天的巡检报告"

                当前处于离线演示模式（Mock 模型），工具调用与推理流程与真实模型一致。试试看吧！
                """;
    }

    private String plainAnswer(String userText) {
        return """
                收到你的问题：%s

                当前为离线演示模式（Mock 模型），无法进行真实推理。你可以：
                1. 尝试演示指令："帮我诊断线上 CPU 告警"、"生成巡检报告"、"统计各服务告警数量"；
                2. 在 `application.yml` 中将 `lingxi.llm.provider` 配置为 `openai` 并填入 API Key（兼容 DeepSeek / 通义千问 / OpenAI）。
                """.formatted(userText.isBlank() ? "（空）" : userText);
    }

    private String findResponse(List<org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse> responses,
                                String tool) {
        if (responses == null) {
            return null;
        }
        return responses.stream()
                .filter(r -> tool.equals(r.name()))
                .map(r -> com.lingxi.common.util.ToolResultUnwrapper.unwrap(r.responseData()))
                .findFirst().orElse(null);
    }

    private String squeeze(String s) {
        if (s == null || s.isBlank()) {
            return "（工具未返回数据）";
        }
        return s.length() <= 900 ? s : s.substring(0, 900) + "\n…（内容过长已截断）";
    }

    private String jsonKeyword(String text) {
        String t = text.length() > 24 ? text.substring(0, 24) : text;
        return t.isBlank() ? "运维 处置" : t;
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
