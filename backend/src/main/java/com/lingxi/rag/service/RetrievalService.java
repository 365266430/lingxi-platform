package com.lingxi.rag.service;

import com.lingxi.rag.KbDocument;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 混合检索：向量召回（top 3x）+ 词面重叠重排（0.7*语义 + 0.3*词面）。
 */
@Service
public class RetrievalService {

    private final VectorStore vectorStore;

    public RetrievalService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public record Hit(Long documentId, String docTitle, int chunkIndex, String content,
                      double semanticScore, double lexicalScore, double finalScore) {
    }

    public List<Hit> retrieve(Long spaceId, String query, int topK) {
        int recall = Math.max(topK * 3, 12);
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(recall)
                .similarityThreshold(0.05)
                .build();
        List<Document> documents = vectorStore.similaritySearch(request);
        List<Hit> hits = new ArrayList<>();
        if (documents != null) {
            for (Document d : documents) {
                Map<String, Object> meta = d.getMetadata();
                Long hitSpace = parseLong(meta.get("spaceId"));
                if (spaceId != null && hitSpace != null && !hitSpace.equals(spaceId)) {
                    continue;
                }
                // Spring AI 约定：metadata 中的 distance 为"距离"（越小越相似），转换为相似度
                double distance = parseDouble(meta.get("distance"), 0.5);
                double semantic = Math.max(0, Math.min(1, 1 - distance));
                double lexical = lexicalOverlap(query, d.getText());
                double finalScore = 0.7 * semantic + 0.3 * lexical;
                hits.add(new Hit(
                        parseLong(meta.get("documentId")),
                        String.valueOf(meta.getOrDefault("docTitle", "未知文档")),
                        (int) parseDouble(meta.get("chunkIndex"), 0),
                        d.getText(),
                        semantic, lexical, finalScore));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::finalScore).reversed());
        return hits.size() > topK ? hits.subList(0, topK) : hits;
    }

    public String retrieveMarkdown(Long spaceId, String query, int topK) {
        List<Hit> hits = retrieve(spaceId, query, topK);
        if (hits.isEmpty()) {
            return "（知识库中未检索到相关内容）";
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (Hit hit : hits) {
            String snippet = hit.content().length() > 400
                    ? hit.content().substring(0, 400) + "…" : hit.content();
            sb.append("**[%d] %s（chunk#%d，相关度 %.3f）**\n%s\n\n"
                    .formatted(i++, hit.docTitle(), hit.chunkIndex(), hit.finalScore(), snippet));
        }
        return sb.toString();
    }

    /** 查询与文档的 char-bigram 词面重叠率（0~1）。 */
    public double lexicalOverlap(String query, String content) {
        Set<String> qGrams = bigrams(query);
        if (qGrams.isEmpty() || content == null || content.isEmpty()) {
            return 0;
        }
        Set<String> cGrams = bigrams(content);
        int hit = 0;
        for (String g : qGrams) {
            if (cGrams.contains(g)) {
                hit++;
            }
        }
        return (double) hit / qGrams.size();
    }

    private Set<String> bigrams(String text) {
        Set<String> grams = new HashSet<>();
        if (text == null) {
            return grams;
        }
        String compact = text.toLowerCase().replaceAll("\\s+", "");
        for (int i = 0; i + 2 <= compact.length(); i++) {
            grams.add(compact.substring(i, i + 2));
        }
        return grams;
    }

    private Long parseLong(Object value) {
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private double parseDouble(Object value, double fallback) {
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
