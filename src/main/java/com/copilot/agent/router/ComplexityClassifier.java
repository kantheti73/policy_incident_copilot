package com.copilot.agent.router;

import com.copilot.config.CopilotProperties;
import com.copilot.model.CopilotRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Classifies incoming requests by complexity and risk level
 * to determine single-agent vs multi-agent routing.
 */
@Slf4j
@Component
public class ComplexityClassifier {

    private final double complexityThreshold;
    private final List<String> highRiskKeywords;

    public ComplexityClassifier(CopilotProperties properties) {
        this.complexityThreshold = properties.getRouting().getComplexityThreshold();
        this.highRiskKeywords = properties.getRouting().getHighRiskKeywords();
    }

    public ClassificationResult classify(CopilotRequest request) {
        double complexityScore = 0.0;
        boolean highRisk = false;

        String query = request.getQuery().toLowerCase();

        // Factor 1: Input length — long queries/logs suggest complexity
        if (query.length() > 500) complexityScore += 0.2;
        if (query.length() > 2000) complexityScore += 0.2;

        // Factor 2: Presence of raw logs indicates incident triage
        if (request.getRawLogs() != null && !request.getRawLogs().isBlank()) {
            complexityScore += 0.3;
        }

        // Factor 3: High-risk keywords trigger multi-agent with safety review
        for (String keyword : highRiskKeywords) {
            if (query.contains(keyword.toLowerCase())) {
                highRisk = true;
                complexityScore += 0.4;
                log.warn("High-risk keyword detected: '{}' in query", keyword);
                break;
            }
        }

        // Factor 4: Multiple questions or conflicting details
        long questionMarks = query.chars().filter(c -> c == '?').count();
        if (questionMarks > 2) complexityScore += 0.2;

        // Factor 5: Keywords suggesting multi-source lookup
        if (query.contains("compare") || query.contains("conflicting") ||
            query.contains("multiple") || query.contains("cross-check")) {
            complexityScore += 0.15;
        }

        complexityScore = Math.min(complexityScore, 1.0);

        boolean isMultiAgent = highRisk || complexityScore >= complexityThreshold;

        log.info("Complexity classification: score={}, highRisk={}, route={}",
                complexityScore, highRisk, isMultiAgent ? "MULTI" : "SINGLE");

        return ClassificationResult.builder()
                .complexityScore(complexityScore)
                .highRisk(highRisk)
                .multiAgentRequired(isMultiAgent)
                .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class ClassificationResult {
        private double complexityScore;
        private boolean highRisk;
        private boolean multiAgentRequired;
    }
}
