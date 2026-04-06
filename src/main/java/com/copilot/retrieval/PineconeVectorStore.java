package com.copilot.retrieval;

import com.copilot.model.PolicySnippet;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Pinecone-backed vector store for policy document embeddings.
 * Uses LangChain4J's EmbeddingStore abstraction over the Pinecone client.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PineconeVectorStore {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public List<PolicySnippet> similaritySearch(String query, int topK) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .minScore(0.5)
                .build();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

        return result.matches().stream()
                .map(this::toPolicySnippet)
                .collect(Collectors.toList());
    }

    public List<PolicySnippet> similaritySearchWithFilter(String query, String category, int topK) {
        // Category-filtered search delegates to the same store with metadata filter
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .minScore(0.5)
                .build();

        return embeddingStore.search(request).matches().stream()
                .map(this::toPolicySnippet)
                .filter(s -> category.equalsIgnoreCase(s.getCategory()))
                .collect(Collectors.toList());
    }

    public void ingest(String documentId, String title, String content,
                       String section, String version, String effectiveDate, String category) {
        Metadata metadata = Metadata.from("documentId", documentId);
        metadata.put("title", title);
        metadata.put("section", section);
        metadata.put("version", version);
        metadata.put("effectiveDate", effectiveDate);
        metadata.put("category", category);

        TextSegment segment = TextSegment.from(content, metadata);
        Embedding embedding = embeddingModel.embed(segment).content();

        embeddingStore.add(embedding, segment);
        log.info("Ingested policy document: {} v{}", title, version);
    }

    private PolicySnippet toPolicySnippet(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        Metadata meta = segment.metadata();

        return PolicySnippet.builder()
                .documentId(meta.getString("documentId"))
                .title(meta.getString("title"))
                .content(segment.text())
                .section(meta.getString("section"))
                .version(meta.getString("version"))
                .effectiveDate(meta.getString("effectiveDate"))
                .category(meta.getString("category"))
                .score(match.score())
                .build();
    }
}
