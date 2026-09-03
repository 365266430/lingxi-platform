package com.lingxi.agent.tools;

import com.lingxi.modules.ops.OpsQueryService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * CMDB 查询工具。
 */
@Component
public class CmdbTool {

    private final OpsQueryService opsQueryService;

    public CmdbTool(OpsQueryService opsQueryService) {
        this.opsQueryService = opsQueryService;
    }

    @Tool(name = "query_cmdb",
            description = "查询 CMDB 配置管理数据库：服务列表（负责人、核心等级、部署主机）与主机信息（IP、配置、环境、状态）")
    public String queryCmdb(@ToolParam(required = false, description = "关键字，匹配服务名/负责人/主机名/IP，留空查全部")
                            String keyword) {
        return opsQueryService.cmdbMarkdown(keyword == null || keyword.isBlank() ? null : keyword.trim());
    }
}
