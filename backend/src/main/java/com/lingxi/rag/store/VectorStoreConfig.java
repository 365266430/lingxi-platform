package com.lingxi.rag.store;

import com.lingxi.agent.llm.ChatModelFactory;
import com.lingxi.config.AppProperties;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量库配置（Qdrant gRPC + 自动建集合）。
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    public QdrantClient qdrantClient(AppProperties properties) {
        AppProperties.Vector vector = properties.getVector();
        return new QdrantClient(QdrantGrpcClient.newBuilder(vector.getHost(), vector.getPort(), false).build());
    }

    @Bean
    public EmbeddingModel platformEmbeddingModel(ChatModelFactory chatModelFactory) {
        return chatModelFactory.getEmbeddingModel();
    }

    @Bean
    public VectorStore vectorStore(QdrantClient qdrantClient, EmbeddingModel platformEmbeddingModel,
                                   AppProperties properties) {
        return QdrantVectorStore.builder(qdrantClient, platformEmbeddingModel)
                .collectionName(properties.getVector().getCollection())
                .initializeSchema(true)
                .build();
    }
}
