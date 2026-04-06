package com.copilot.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for LangChain4J components: embedding model and vector store.
 */
@Configuration
public class LangChainConfig {

    @Value("${pinecone.api-key:}")
    private String pineconeApiKey;

    @Value("${pinecone.index-name:policy-documents}")
    private String pineconeIndex;

    @Value("${pinecone.namespace:policies}")
    private String pineconeNamespace;

    @Value("${pinecone.environment:us-east-1}")
    private String pineconeEnvironment;

    @Bean
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        if (pineconeApiKey == null || pineconeApiKey.isBlank()) {
            // Return an in-memory store for development/testing
            return new dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore<>();
        }

        return PineconeEmbeddingStore.builder()
                .apiKey(pineconeApiKey)
                .index(pineconeIndex)
                .nameSpace(pineconeNamespace)
                .build();
    }
}
