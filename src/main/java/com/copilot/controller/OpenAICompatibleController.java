package com.copilot.controller;

import com.copilot.model.CopilotRequest;
import com.copilot.model.CopilotResponse;
import com.copilot.service.CopilotService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OpenAI-compatible API endpoints that Open WebUI connects to.
 * Translates between OpenAI chat completion format and our CopilotService.
 *
 * Supports both non-streaming and streaming (SSE) responses.
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class OpenAICompatibleController {

    private final CopilotService copilotService;
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    /**
     * GET /v1/models — Lists available models for Open WebUI model selector.
     */
    @GetMapping("/models")
    public ResponseEntity<ModelListResponse> listModels() {
        ModelListResponse response = new ModelListResponse();
        response.setObject("list");
        response.setData(List.of(
                ModelInfo.builder()
                        .id("policy-copilot")
                        .object("model")
                        .created(Instant.now().getEpochSecond())
                        .ownedBy("policy-incident-copilot")
                        .build(),
                ModelInfo.builder()
                        .id("incident-triage")
                        .object("model")
                        .created(Instant.now().getEpochSecond())
                        .ownedBy("policy-incident-copilot")
                        .build()
        ));
        return ResponseEntity.ok(response);
    }

    /**
     * POST /v1/chat/completions — Main chat endpoint consumed by Open WebUI.
     * Routes to streaming or non-streaming based on the request.
     */
    @PostMapping("/chat/completions")
    public Object chatCompletions(@RequestBody ChatCompletionRequest request) {
        log.info("Chat completion request: model={}, messages={}, stream={}",
                request.getModel(), request.getMessages().size(), request.isStream());

        if (request.isStream()) {
            return handleStreaming(request);
        }
        return handleNonStreaming(request);
    }

    private ResponseEntity<ChatCompletionResponse> handleNonStreaming(ChatCompletionRequest request) {
        CopilotRequest copilotRequest = translateRequest(request);
        CopilotResponse copilotResponse = copilotService.process(copilotRequest);

        String responseContent = formatResponse(copilotResponse);

        ChatCompletionResponse response = ChatCompletionResponse.builder()
                .id("chatcmpl-" + UUID.randomUUID().toString().substring(0, 12))
                .object("chat.completion")
                .created(Instant.now().getEpochSecond())
                .model(request.getModel())
                .choices(List.of(
                        Choice.builder()
                                .index(0)
                                .message(Message.builder()
                                        .role("assistant")
                                        .content(responseContent)
                                        .build())
                                .finishReason("stop")
                                .build()
                ))
                .usage(Usage.builder()
                        .promptTokens(estimateTokens(request))
                        .completionTokens(estimateTokens(responseContent))
                        .totalTokens(estimateTokens(request) + estimateTokens(responseContent))
                        .build())
                .build();

        return ResponseEntity.ok(response);
    }

    private SseEmitter handleStreaming(ChatCompletionRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L); // 2 min timeout

        streamExecutor.submit(() -> {
            try {
                String completionId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 12);

                // Send role chunk
                sendStreamChunk(emitter, completionId, request.getModel(),
                        new Delta("assistant", null), null);

                // Process request
                CopilotRequest copilotRequest = translateRequest(request);
                CopilotResponse copilotResponse = copilotService.process(copilotRequest);
                String responseContent = formatResponse(copilotResponse);

                // Stream the response in chunks to simulate token-by-token delivery
                String[] words = responseContent.split("(?<=\\s)");
                StringBuilder buffer = new StringBuilder();
                int chunkSize = 3; // Send ~3 words per chunk for smooth UX

                for (int i = 0; i < words.length; i++) {
                    buffer.append(words[i]);
                    if ((i + 1) % chunkSize == 0 || i == words.length - 1) {
                        sendStreamChunk(emitter, completionId, request.getModel(),
                                new Delta(null, buffer.toString()), null);
                        buffer.setLength(0);
                        Thread.sleep(30); // Small delay for streaming effect
                    }
                }

                // Send final chunk with finish_reason
                sendStreamChunk(emitter, completionId, request.getModel(),
                        new Delta(null, null), "stop");

                // Send [DONE]
                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();

            } catch (Exception e) {
                log.error("Streaming error: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event().data("[DONE]"));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void sendStreamChunk(SseEmitter emitter, String id, String model,
                                  Delta delta, String finishReason) throws IOException {
        StreamChunk chunk = StreamChunk.builder()
                .id(id)
                .object("chat.completion.chunk")
                .created(Instant.now().getEpochSecond())
                .model(model)
                .choices(List.of(
                        StreamChoice.builder()
                                .index(0)
                                .delta(delta)
                                .finishReason(finishReason)
                                .build()
                ))
                .build();

        emitter.send(SseEmitter.event().data(chunk, MediaType.APPLICATION_JSON));
    }

    /**
     * Translate OpenAI chat format into our CopilotRequest.
     * Extracts the last user message as the query, and uses conversation history for context.
     */
    private CopilotRequest translateRequest(ChatCompletionRequest request) {
        // Find the last user message as the primary query
        String query = "";
        String rawLogs = null;
        for (int i = request.getMessages().size() - 1; i >= 0; i--) {
            Message msg = request.getMessages().get(i);
            if ("user".equals(msg.getRole())) {
                query = msg.getContent();
                break;
            }
        }

        // Detect if logs are embedded in the query (heuristic: lines with timestamps)
        if (query.lines().filter(line -> line.matches("^\\d{4}-\\d{2}-\\d{2}.*")).count() > 3) {
            // Separate query text from pasted logs
            StringBuilder queryPart = new StringBuilder();
            StringBuilder logPart = new StringBuilder();
            for (String line : query.split("\n")) {
                if (line.matches("^\\d{4}-\\d{2}-\\d{2}.*")) {
                    logPart.append(line).append("\n");
                } else {
                    queryPart.append(line).append("\n");
                }
            }
            query = queryPart.toString().trim();
            rawLogs = logPart.toString().trim();
        }

        // Force multi-agent mode if "incident-triage" model is selected
        CopilotRequest.AgentMode forceMode = null;
        if ("incident-triage".equals(request.getModel())) {
            forceMode = CopilotRequest.AgentMode.MULTI;
        }

        return CopilotRequest.builder()
                .query(query)
                .rawLogs(rawLogs)
                .sessionId(extractSessionId(request))
                .userId("webui-user")
                .forceMode(forceMode)
                .build();
    }

    /**
     * Format the CopilotResponse into a rich markdown response for the chat UI.
     */
    private String formatResponse(CopilotResponse response) {
        StringBuilder sb = new StringBuilder();

        sb.append(response.getAnswer());

        // Append citations
        if (response.getCitations() != null && !response.getCitations().isEmpty()) {
            sb.append("\n\n---\n**Sources:**\n");
            for (CopilotResponse.Citation c : response.getCitations()) {
                sb.append(String.format("- **%s** v%s (%s) — relevance: %.0f%%\n",
                        c.getDocumentTitle(), c.getVersion(),
                        c.getEffectiveDate(), c.getRelevanceScore() * 100));
            }
        }

        // Append triage ticket if present
        if (response.getTriageTicket() != null) {
            var ticket = response.getTriageTicket();
            sb.append("\n\n---\n**Triage Ticket:**\n");
            sb.append(String.format("- **Severity:** %s\n", ticket.getSeverity()));
            sb.append(String.format("- **Category:** %s\n", ticket.getCategory()));
            sb.append(String.format("- **Affected Systems:** %s\n", String.join(", ", ticket.getAffectedSystems())));
            sb.append(String.format("- **Root Cause Hypothesis:** %s\n", ticket.getRootCauseHypothesis()));
            sb.append(String.format("- **Escalation:** %s\n", ticket.getEscalationPath()));
            if (ticket.isRequiresHumanReview()) {
                sb.append("- **⚠ Requires human review**\n");
            }
        }

        // Append guardrail warnings
        if (response.getGuardrailFlags() != null && !response.getGuardrailFlags().isEmpty()) {
            sb.append("\n\n> **Safety Notes:** ");
            sb.append(String.join("; ", response.getGuardrailFlags()));
        }

        // Metadata footer
        sb.append(String.format("\n\n<sub>Mode: %s | Steps: %d | Latency: %dms | Trace: %s</sub>",
                response.getModeUsed(), response.getStepsExecuted(),
                response.getLatencyMs(), response.getTraceId()));

        return sb.toString();
    }

    private String extractSessionId(ChatCompletionRequest request) {
        // Use a hash of the first system message or generate one
        return "webui-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private int estimateTokens(ChatCompletionRequest request) {
        return request.getMessages().stream()
                .mapToInt(m -> m.getContent() != null ? m.getContent().length() / 4 : 0)
                .sum();
    }

    private int estimateTokens(String text) {
        return text != null ? text.length() / 4 : 0;
    }

    // ===== OpenAI-compatible DTOs =====

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatCompletionRequest {
        private String model;
        private List<Message> messages;
        private boolean stream;
        private Double temperature;
        @JsonProperty("max_tokens")
        private Integer maxTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        private String role;
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatCompletionResponse {
        private String id;
        private String object;
        private long created;
        private String model;
        private List<Choice> choices;
        private Usage usage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Choice {
        private int index;
        private Message message;
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private int promptTokens;
        @JsonProperty("completion_tokens")
        private int completionTokens;
        @JsonProperty("total_tokens")
        private int totalTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StreamChunk {
        private String id;
        private String object;
        private long created;
        private String model;
        private List<StreamChoice> choices;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StreamChoice {
        private int index;
        private Delta delta;
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Delta {
        private String role;
        private String content;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelListResponse {
        private String object;
        private List<ModelInfo> data;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelInfo {
        private String id;
        private String object;
        private long created;
        @JsonProperty("owned_by")
        private String ownedBy;
    }
}
