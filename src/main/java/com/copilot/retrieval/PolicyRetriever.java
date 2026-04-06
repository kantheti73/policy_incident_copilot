package com.copilot.retrieval;

import com.copilot.model.PolicySnippet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Unified policy retrieval interface that queries Pinecone vector store
 * and enriches results with metadata.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyRetriever {

    private final PineconeVectorStore vectorStore;

    public List<PolicySnippet> retrieve(String query, int topK) {
        log.debug("Retrieving top {} policy snippets for query: {}", topK, truncate(query, 100));

        List<PolicySnippet> results = vectorStore.similaritySearch(query, topK);

        log.info("Retrieved {} policy snippets (top score: {})",
                results.size(),
                results.isEmpty() ? "N/A" : String.format("%.3f", results.get(0).getScore()));

        return results;
    }

    public List<PolicySnippet> retrieveByCategory(String query, String category, int topK) {
        log.debug("Retrieving policies for category '{}': {}", category, truncate(query, 100));
        return vectorStore.similaritySearchWithFilter(query, category, topK);
    }

    private String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
