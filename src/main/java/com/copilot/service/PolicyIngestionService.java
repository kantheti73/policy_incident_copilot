package com.copilot.service;

import com.copilot.retrieval.DocumentChunker;
import com.copilot.retrieval.PineconeVectorStore;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for ingesting policy documents into the vector store.
 * Handles chunking, embedding, and metadata association.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyIngestionService {

    private final DocumentChunker documentChunker;
    private final PineconeVectorStore vectorStore;

    /**
     * Ingest a policy document by chunking it and storing each chunk
     * with metadata in Pinecone.
     */
    public int ingestPolicy(String documentId, String title, String content,
                            String version, String effectiveDate, String category) {
        log.info("Ingesting policy: {} v{} (category: {})", title, version, category);

        List<TextSegment> chunks = documentChunker.chunkText(content, title);

        int chunkIndex = 0;
        for (TextSegment chunk : chunks) {
            String chunkId = documentId + "-chunk-" + chunkIndex;
            vectorStore.ingest(
                    chunkId,
                    title,
                    chunk.text(),
                    "chunk-" + chunkIndex,
                    version,
                    effectiveDate,
                    category
            );
            chunkIndex++;
        }

        log.info("Successfully ingested {} chunks for policy: {}", chunks.size(), title);
        return chunks.size();
    }
}
