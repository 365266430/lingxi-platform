package com.lingxi.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingxi.config.AppProperties;
import com.lingxi.rag.KbChunk;
import com.lingxi.rag.KbChunkMapper;
import com.lingxi.rag.KbDocument;
import com.lingxi.rag.KbDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文档摄取：解析（Tika）→ 分块 → 向量化入 Qdrant → 分块留档 MySQL。
 * 设计要点：chunk 的 vectorId 由 docId+index 确定性生成，支持幂等重灌与精确删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final ChunkingService chunkingService;
    private final VectorStore vectorStore;
    private final AppProperties properties;

    public void ingest(Long documentId, byte[] content, String filename) {
        KbDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            log.warn("文档不存在，跳过摄取 id={}", documentId);
            return;
        }
        try {
            updateStatus(doc, "PROCESSING", null);
            String text = extractText(content);
            List<String> chunks = chunkingService.chunk(text, 600, 120);
            if (chunks.isEmpty()) {
                chunks = List.of("（文档无有效文本内容）");
            }
            deleteVectors(doc);

            List<Document> vectorDocs = new ArrayList<>(chunks.size());
            List<KbChunk> chunkRows = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                String vectorId = vectorIdOf(doc.getId(), i);
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("spaceId", String.valueOf(doc.getSpaceId()));
                metadata.put("documentId", String.valueOf(doc.getId()));
                metadata.put("docTitle", doc.getFilename());
                metadata.put("chunkIndex", i);
                vectorDocs.add(Document.builder()
                        .id(vectorId)
                        .text(chunks.get(i))
                        .metadata(metadata)
                        .build());
                KbChunk row = new KbChunk();
                row.setDocumentId(doc.getId());
                row.setSpaceId(doc.getSpaceId());
                row.setChunkIndex(i);
                row.setContent(chunks.get(i));
                row.setVectorId(vectorId);
                chunkRows.add(row);
            }
            vectorStore.add(vectorDocs);
            chunkRows.forEach(chunkMapper::insert);

            KbDocument update = new KbDocument();
            update.setId(doc.getId());
            update.setStatus("COMPLETED");
            update.setChunkCount(chunks.size());
            update.setErrorMessage(null);
            update.setUpdatedAt(java.time.LocalDateTime.now());
            documentMapper.updateById(update);
            log.info("文档摄取完成 id={} file={} chunks={}", doc.getId(), doc.getFilename(), chunks.size());
        } catch (Exception e) {
            log.error("文档摄取失败 id={}", documentId, e);
            updateStatus(doc, "FAILED", e.getMessage());
            throw new IllegalStateException("文档摄取失败: " + e.getMessage(), e);
        }
    }

    public void deleteVectors(KbDocument doc) {
        List<KbChunk> existing = chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getDocumentId, doc.getId()));
        if (!existing.isEmpty()) {
            List<String> ids = existing.stream().map(KbChunk::getVectorId).toList();
            try {
                vectorStore.delete(ids);
            } catch (Exception e) {
                log.warn("向量删除失败（可能集合尚未创建）docId={}: {}", doc.getId(), e.getMessage());
            }
            chunkMapper.delete(new LambdaQueryWrapper<KbChunk>()
                    .eq(KbChunk::getDocumentId, doc.getId()));
        }
    }

    public String vectorIdOf(Long documentId, int index) {
        return UUID.nameUUIDFromBytes((documentId + "-" + index)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    public String extractText(byte[] content) {
        TikaDocumentReader reader = new TikaDocumentReader(new ByteArrayResource(content));
        List<Document> documents = reader.read();
        StringBuilder sb = new StringBuilder();
        for (Document d : documents) {
            String t = d.getText();
            if (t != null) {
                sb.append(t).append('\n');
            }
        }
        return sb.toString();
    }

    private void updateStatus(KbDocument doc, String status, String error) {
        KbDocument update = new KbDocument();
        update.setId(doc.getId());
        update.setStatus(status);
        update.setErrorMessage(error);
        update.setUpdatedAt(java.time.LocalDateTime.now());
        documentMapper.updateById(update);
    }
}
