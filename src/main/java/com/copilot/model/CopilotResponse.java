package com.copilot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotResponse {

    private String traceId;
    private String sessionId;
    private String answer;
    private AgentModeUsed modeUsed;
    private List<Citation> citations;
    private TriageTicket triageTicket;
    private int stepsExecuted;
    private long latencyMs;
    private Instant timestamp;
    private List<String> guardrailFlags;

    public enum AgentModeUsed {
        SINGLE_AGENT, MULTI_AGENT
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Citation {
        private String documentId;
        private String documentTitle;
        private String snippet;
        private String version;
        private String effectiveDate;
        private double relevanceScore;
    }
}
