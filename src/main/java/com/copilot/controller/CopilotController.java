package com.copilot.controller;

import com.copilot.model.CopilotRequest;
import com.copilot.model.CopilotResponse;
import com.copilot.service.CopilotService;
import com.copilot.service.PolicyIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for the Policy & Incident Copilot.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/copilot")
@RequiredArgsConstructor
public class CopilotController {

    private final CopilotService copilotService;
    private final PolicyIngestionService ingestionService;

    /**
     * Main copilot endpoint: accepts a query and returns an AI-assisted response.
     */
    @PostMapping("/query")
    public ResponseEntity<CopilotResponse> query(@Valid @RequestBody CopilotRequest request) {
        log.info("Received copilot query from user={}, length={}",
                request.getUserId(), request.getQuery().length());

        CopilotResponse response = copilotService.process(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Policy ingestion endpoint: upload a policy document for indexing.
     */
    @PostMapping("/ingest")
    public ResponseEntity<IngestionResponse> ingestPolicy(@RequestBody PolicyIngestionRequest request) {
        log.info("Ingesting policy: {}", request.title());

        int chunks = ingestionService.ingestPolicy(
                request.documentId(),
                request.title(),
                request.content(),
                request.version(),
                request.effectiveDate(),
                request.category()
        );

        return ResponseEntity.ok(new IngestionResponse(request.documentId(), chunks, "SUCCESS"));
    }

    /**
     * Health check endpoint for the copilot service.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Policy & Incident Copilot is running");
    }

    // Request/Response records for ingestion
    record PolicyIngestionRequest(
            String documentId,
            String title,
            String content,
            String version,
            String effectiveDate,
            String category
    ) {}

    record IngestionResponse(String documentId, int chunksCreated, String status) {}
}
