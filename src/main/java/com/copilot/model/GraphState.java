package com.copilot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared state object passed through LangGraph4J graph nodes.
 * Each agent reads from and writes to this state.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphState {

    private String traceId;
    private String sessionId;
    private String originalQuery;
    private String sanitizedQuery;
    private String rawLogs;

    /** Routing decision */
    @Builder.Default
    private RoutingDecision routingDecision = RoutingDecision.SINGLE;

    /** Retrieved policy snippets */
    @Builder.Default
    private List<PolicySnippet> retrievedPolicies = new ArrayList<>();

    /** Multi-agent: plan decomposed by the Planner */
    @Builder.Default
    private List<String> plan = new ArrayList<>();

    /** Multi-agent: research findings from the Researcher */
    @Builder.Default
    private Map<String, String> researchFindings = new HashMap<>();

    /** Draft response before critic review */
    private String draftResponse;

    /** Final response after critic/safety review */
    private String finalResponse;

    /** Triage ticket (populated for incident flows) */
    private TriageTicket triageTicket;

    /** Guardrail flags raised during processing */
    @Builder.Default
    private List<String> guardrailFlags = new ArrayList<>();

    /** Step counter for enforcing limits */
    @Builder.Default
    private int currentStep = 0;

    @Builder.Default
    private int maxSteps = 10;

    /** Error tracking */
    private String error;

    @Builder.Default
    private boolean safetyApproved = false;

    public enum RoutingDecision {
        SINGLE, MULTI
    }

    public void incrementStep() {
        this.currentStep++;
    }

    public boolean isStepLimitReached() {
        return currentStep >= maxSteps;
    }
}
