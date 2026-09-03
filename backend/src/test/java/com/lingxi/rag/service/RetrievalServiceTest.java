package com.lingxi.rag.service;

import com.lingxi.agent.llm.mock.MockEmbeddingModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 混合检索测试：内存向量库 + 离线 Embedding。
 */
class RetrievalServiceTest {

    private RetrievalService retrievalService;
    private VectorStore vectorStore;

    @BeforeEach
    void setUp() {
        vectorStore = SimpleVectorStore.builder(new MockEmbeddingModel()).build();
        vectorStore.add(List.of(
                Document.builder().id("d1").text(
                                "磁盘空间不足处置手册：使用 df -h 定位分区，du 找出大目录，清理过期日志并配置日志轮转，必要时扩容云盘。")
                        .metadata(Map.of("spaceId", "1", "docTitle", "磁盘空间处置手册.md", "chunkIndex", 0,
                                "documentId", "10"))
                        .build(),
                Document.builder().id("d2").text(
                                "CPU 飙高排查手册：top -Hp 定位高 CPU 线程，jstack 查看线程栈，区分业务热点、GC 频繁与锁竞争。")
                        .metadata(Map.of("spaceId", "1", "docTitle", "CPU排查手册.md", "chunkIndex", 0,
                                "documentId", "11"))
                        .build(),
                Document.builder().id("d3").text(
                                "Redis 大 Key 处置：使用 --bigkeys 扫描，异步 unlink 删除，拆分大集合并设置过期时间。")
                        .metadata(Map.of("spaceId", "2", "docTitle", "Redis手册.md", "chunkIndex", 0,
                                "documentId", "12"))
                        .build()));
        retrievalService = new RetrievalService(vectorStore);
    }

    @Test
    @DisplayName("磁盘问题检索命中磁盘手册且排名第一")
    void retrieveDiskRunbookFirst() {
        List<RetrievalService.Hit> hits = retrievalService.retrieve(1L, "磁盘空间不足 怎么清理日志", 2);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).docTitle()).isEqualTo("磁盘空间处置手册.md");
    }

    @Test
    @DisplayName("CPU 问题检索命中 CPU 手册")
    void retrieveCpuRunbook() {
        List<RetrievalService.Hit> hits = retrievalService.retrieve(null, "CPU 飙高 jstack 排查线程", 2);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).docTitle()).isEqualTo("CPU排查手册.md");
    }

    @Test
    @DisplayName("spaceId 过滤生效：空间 1 检索不到 Redis 文档")
    void spaceFilterWorks() {
        List<RetrievalService.Hit> hits = retrievalService.retrieve(1L, "Redis 大 Key unlink 删除", 3);
        assertThat(hits).noneMatch(h -> h.docTitle().equals("Redis手册.md"));
    }

    @Test
    @DisplayName("Markdown 输出带来源与相关度")
    void markdownOutput() {
        String markdown = retrievalService.retrieveMarkdown(1L, "磁盘空间不足 怎么清理", 2);
        assertThat(markdown).contains("磁盘空间处置手册.md").contains("相关度");
    }

    @Test
    @DisplayName("词面重叠评分在区间内且方向正确")
    void lexicalOverlap() {
        double related = retrievalService.lexicalOverlap("磁盘空间不足", "磁盘空间不足处置手册：清理日志");
        double unrelated = retrievalService.lexicalOverlap("磁盘空间不足", "Redis 大 Key 扫描");
        assertThat(related).isGreaterThan(unrelated);
        assertThat(related).isBetween(0.0, 1.0);
    }
}
