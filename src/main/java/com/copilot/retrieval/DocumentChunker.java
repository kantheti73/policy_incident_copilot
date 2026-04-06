package com.copilot.retrieval;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Chunks policy documents into smaller segments for embedding and retrieval.
 * Uses recursive character splitting with overlap for context preservation.
 */
@Slf4j
@Component
public class DocumentChunker {

    private static final int CHUNK_SIZE = 512;
    private static final int CHUNK_OVERLAP = 64;

    private final DocumentSplitter splitter;

    public DocumentChunker() {
        this.splitter = DocumentSplitters.recursive(CHUNK_SIZE, CHUNK_OVERLAP);
    }

    public List<TextSegment> chunk(Document document) {
        List<TextSegment> segments = splitter.split(document);
        log.info("Chunked document '{}' into {} segments",
                document.metadata().getString("title"), segments.size());
        return segments;
    }

    public List<TextSegment> chunkText(String text, String title) {
        Document doc = Document.from(text);
        doc.metadata().put("title", title);
        return chunk(doc);
    }
}
