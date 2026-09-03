package com.lingxi.rag.pipeline;

import com.lingxi.config.RabbitConfig;
import com.lingxi.rag.KbDocument;
import com.lingxi.rag.KbDocumentMapper;
import com.lingxi.rag.service.FileStorageService;
import com.lingxi.rag.service.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 文档摄取消费者：MinIO 取文件 → 摄取管道。失败重试 3 次后进入死信队列。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocIngestConsumer {

    private final IngestionService ingestionService;
    private final FileStorageService fileStorageService;
    private final KbDocumentMapper documentMapper;

    @RabbitListener(queues = RabbitConfig.DOC_QUEUE)
    public void onMessage(DocIngestMessage message) {
        log.info("收到文档摄取消息 docId={} file={}", message.documentId(), message.filename());
        try {
            byte[] content = fileStorageService.download(message.objectKey());
            ingestionService.ingest(message.documentId(), content, message.filename());
        } catch (Exception e) {
            log.error("文档摄取失败（将重试）docId={}", message.documentId(), e);
            markFailed(message.documentId(), e);
            throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
        }
    }

    private void markFailed(Long documentId, Exception e) {
        try {
            KbDocument update = new KbDocument();
            update.setId(documentId);
            update.setStatus("FAILED");
            update.setErrorMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            update.setUpdatedAt(java.time.LocalDateTime.now());
            documentMapper.updateById(update);
        } catch (Exception ignore) {
            // 数据库不可用时的兜底，避免掩盖原始异常
        }
    }
}
