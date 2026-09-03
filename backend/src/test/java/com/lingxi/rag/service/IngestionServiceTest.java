package com.lingxi.rag.service;

import com.lingxi.agent.llm.mock.MockEmbeddingModel;
import com.lingxi.config.AppProperties;
import com.lingxi.rag.KbChunk;
import com.lingxi.rag.KbChunkMapper;
import com.lingxi.rag.KbDocument;
import com.lingxi.rag.KbDocumentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档摄取管道测试：真实分块 + 内存向量库 + Mock 持久层。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IngestionServiceTest {

    @Mock
    private KbDocumentMapper documentMapper;
    @Mock
    private KbChunkMapper chunkMapper;

    private VectorStore vectorStore;
    private IngestionService ingestionService;

    @BeforeEach
    void setUp() {
        vectorStore = SimpleVectorStore.builder(new MockEmbeddingModel()).build();
        ingestionService = new IngestionService(documentMapper, chunkMapper,
                new ChunkingService(), vectorStore, new AppProperties());
    }

    private KbDocument sampleDocument() {
        KbDocument doc = new KbDocument();
        doc.setId(100L);
        doc.setSpaceId(1L);
        doc.setFilename("运维手册.md");
        doc.setStatus("PROCESSING");
        return doc;
    }

    private byte[] longMarkdown() {
        StringBuilder sb = new StringBuilder("# 处置手册\n\n");
        for (int i = 1; i <= 40; i++) {
            sb.append("## 步骤 ").append(i).append("\n第").append(i)
                    .append("步：登录堡垒机检查目标主机状态，采集系统指标与日志关键行，确认异常是否收敛，并记录处置时间线。\n\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("摄取成功：分块入库、向量写入、状态 COMPLETED")
    void ingestSuccess() {
        KbDocument doc = sampleDocument();
        when(documentMapper.selectById(100L)).thenReturn(doc);

        ingestionService.ingest(100L, longMarkdown(), "运维手册.md");

        ArgumentCaptor<KbChunk> chunkCaptor = ArgumentCaptor.forClass(KbChunk.class);
        verify(chunkMapper, atLeast(2)).insert(chunkCaptor.capture());
        List<KbChunk> chunks = chunkCaptor.getAllValues();
        assertThat(chunks).allSatisfy(c -> {
            assertThat(c.getDocumentId()).isEqualTo(100L);
            assertThat(c.getVectorId()).isNotBlank();
            assertThat(c.getContent()).isNotBlank();
        });
        // vectorId 确定性（幂等重灌）
        assertThat(chunks.get(0).getVectorId()).isEqualTo(ingestionService.vectorIdOf(100L, 0));

        ArgumentCaptor<KbDocument> docCaptor = ArgumentCaptor.forClass(KbDocument.class);
        verify(documentMapper, atLeast(2)).updateById(docCaptor.capture());
        KbDocument finalUpdate = docCaptor.getAllValues().get(docCaptor.getAllValues().size() - 1);
        assertThat(finalUpdate.getStatus()).isEqualTo("COMPLETED");
        assertThat(finalUpdate.getChunkCount()).isEqualTo(chunks.size());

        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query("登录堡垒机 检查主机 采集指标").topK(2).build());
        assertThat(hits).isNotEmpty();
    }

    @Test
    @DisplayName("摄取失败：状态置为 FAILED 并抛出异常")
    void ingestFailureMarksFailed() {
        KbDocument doc = sampleDocument();
        when(documentMapper.selectById(100L)).thenReturn(doc);
        doThrow(new RuntimeException("模拟向量库故障")).when(chunkMapper).insert(any(KbChunk.class));

        try {
            ingestionService.ingest(100L, longMarkdown(), "运维手册.md");
        } catch (RuntimeException expected) {
            // 预期抛出
        }
        ArgumentCaptor<KbDocument> docCaptor = ArgumentCaptor.forClass(KbDocument.class);
        verify(documentMapper, atLeast(2)).updateById(docCaptor.capture());
        KbDocument lastUpdate = docCaptor.getAllValues().get(docCaptor.getAllValues().size() - 1);
        assertThat(lastUpdate.getStatus()).isEqualTo("FAILED");
        assertThat(lastUpdate.getErrorMessage()).contains("模拟向量库故障");
    }

    @Test
    @DisplayName("重复摄取前先清理旧向量（幂等）")
    void reingestCleansOldVectors() {
        KbDocument doc = sampleDocument();
        when(documentMapper.selectById(100L)).thenReturn(doc);
        ingestionService.ingest(100L, longMarkdown(), "运维手册.md");
        int firstCount = vectorStore.similaritySearch(
                SearchRequest.builder().query("处置手册").topK(100).build()).size();
        ingestionService.ingest(100L, longMarkdown(), "运维手册.md");
        int secondCount = vectorStore.similaritySearch(
                SearchRequest.builder().query("处置手册").topK(100).build()).size();
        assertThat(secondCount).isEqualTo(firstCount);
    }
}
