package com.lingxi.rag.pipeline;

/**
 * 文档摄取消息体。
 */
public record DocIngestMessage(Long documentId, String objectKey, String filename) {
}
