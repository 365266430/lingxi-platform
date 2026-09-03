package com.lingxi.agent.llm.mock;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 离线 Embedding 模型：char-bigram + 词元哈希词袋向量（512 维，L2 归一化）。
 * 中文场景下相似文本共享大量 bigram，余弦相似度可支持 RAG 演示与测试。
 */
public class MockEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 512;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        AtomicInteger index = new AtomicInteger();
        List<Embedding> embeddings = request.getInstructions().stream()
                .map(text -> new Embedding(embed(text), index.getAndIncrement()))
                .toList();
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text == null || text.isEmpty()) {
            return vector;
        }
        String normalized = text.toLowerCase();
        // 词元（英文按词、中文按单字）
        for (String token : normalized.split("[^\\p{L}\\p{N}]+")) {
            if (!token.isEmpty()) {
                add(vector, token, 1.5f);
                if (token.length() > 2) {
                    for (int i = 0; i + 2 <= token.length(); i++) {
                        add(vector, token.substring(i, i + 2), 1.0f);
                    }
                }
            }
        }
        // 字符 bigram（对 CJK 连续文本尤其有效）
        String compact = normalized.replaceAll("\\s+", "");
        for (int i = 0; i + 2 <= compact.length(); i++) {
            add(vector, compact.substring(i, i + 2), 0.8f);
        }
        // L2 归一化
        double norm = 0;
        for (float v : vector) {
            norm += (double) v * v;
        }
        if (norm > 0) {
            float inv = (float) (1.0 / Math.sqrt(norm));
            for (int i = 0; i < vector.length; i++) {
                vector[i] *= inv;
            }
        }
        return vector;
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    public int getDimensions() {
        return DIMENSIONS;
    }

    private void add(float[] vector, String token, float weight) {
        int slot = Math.floorMod(token.hashCode(), DIMENSIONS);
        vector[slot] += weight;
    }
}
