package com.lingxi.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 联网搜索工具（演示模式：模拟实现，可替换为 Tavily/SerpAPI 等真实供应商）。
 */
@Component
public class WebSearchTool {

    @Tool(name = "web_search",
            description = "联网搜索公开资料。当前为演示模式的模拟实现，返回带标记的模拟结果")
    public String search(@ToolParam(description = "搜索关键词") String query) {
        return """
                【SIMULATED】演示模式下未发起真实网络请求，以下为模拟搜索结果：
                1. 《%s 相关的行业标准实践》—— 摘要：建议建立可观测性基线并定期演练。
                2. 《企业级 AIOps 落地白皮书（节选）》—— 摘要：告警收敛、Runbook 自动化与根因定位是三大关键场景。
                3. 《高可用服务治理案例集》—— 摘要：容量规划与限流降级预案需覆盖核心链路。
                """.formatted(query == null || query.isBlank() ? "运维" : query);
    }
}
