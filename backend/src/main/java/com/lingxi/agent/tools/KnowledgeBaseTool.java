package com.lingxi.agent.tools;

import com.lingxi.rag.service.RetrievalService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 知识库检索工具（RAG）。
 */
@Component
public class KnowledgeBaseTool {

    private final RetrievalService retrievalService;

    public KnowledgeBaseTool(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Tool(name = "search_knowledge_base",
            description = "在企业知识库（运维手册、故障案例、规范文档）中检索与问题最相关的片段，返回带来源标题与相关度的 Markdown 列表")
    public String search(@ToolParam(description = "检索问题或关键词，建议 5~30 字") String query) {
        return retrievalService.retrieveMarkdown(null, query, 4);
    }
}
