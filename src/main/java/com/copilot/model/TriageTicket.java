package com.copilot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriageTicket {

    private String incidentSummary;
    private Severity severity;
    private String category;
    private List<String> affectedSystems;
    private int estimatedUsersImpacted;
    private String rootCauseHypothesis;
    private List<String> recommendedActions;
    private List<String> relevantPolicies;
    private String escalationPath;
    private boolean requiresHumanReview;

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
