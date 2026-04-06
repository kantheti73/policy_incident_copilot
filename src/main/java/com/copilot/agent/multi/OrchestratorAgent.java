package com.copilot.agent.multi;

import com.copilot.model.GraphState;
import com.copilot.model.TriageTicket;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Orchestrator agent: synthesizes research findings into a draft response
 * and produces triage tickets for incident flows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrchestratorAgent {

    private final ChatLanguageModel chatModel;

    private static final String SYSTEM_PROMPT = """
            You are an Orchestrator agent that synthesizes research findings into a coherent response.

            Your responsibilities:
            1. Combine findings from all research steps into a unified answer.
            2. For incident triage requests, produce a structured triage ticket with:
               - Incident summary
               - Severity (LOW/MEDIUM/HIGH/CRITICAL)
               - Affected systems
               - Root cause hypothesis
               - Recommended actions
               - Relevant policies
               - Escalation path
            3. Ensure all claims are supported by the research findings.
            4. Cite relevant policy documents.

            For incident triage, output the triage ticket fields clearly labeled.
            For policy questions, provide a comprehensive cited answer.
            """;

    public GraphState execute(GraphState state) {
        log.info("[{}] OrchestratorAgent synthesizing response", state.getTraceId());
        state.incrementStep();

        String findingsSummary = state.getResearchFindings().entrySet().stream()
                .map(e -> "## " + e.getKey() + "\n" + e.getValue())
                .collect(Collectors.joining("\n\n"));

        boolean isIncidentFlow = state.getRawLogs() != null && !state.getRawLogs().isBlank();

        String synthesisPrompt = String.format("""
                Original request: %s

                Research findings:
                %s

                %s

                Synthesize these findings into a %s.
                """,
                state.getSanitizedQuery(),
                findingsSummary,
                isIncidentFlow ? "This is an INCIDENT TRIAGE request. Produce a triage ticket." : "",
                isIncidentFlow ? "triage ticket and response" : "comprehensive policy answer with citations");

        AiMessage response = chatModel.generate(
                SystemMessage.from(SYSTEM_PROMPT),
                UserMessage.from(synthesisPrompt)
        ).content();

        state.setDraftResponse(response.text());

        // Build triage ticket for incident flows
        if (isIncidentFlow) {
            state.setTriageTicket(buildTriageTicket(response.text(), state));
        }

        log.info("[{}] OrchestratorAgent draft complete", state.getTraceId());
        return state;
    }

    private TriageTicket buildTriageTicket(String llmOutput, GraphState state) {
        // Parse structured output from LLM into a TriageTicket
        return TriageTicket.builder()
                .incidentSummary(extractField(llmOutput, "Incident summary", state.getSanitizedQuery()))
                .severity(parseSeverity(llmOutput))
                .category(extractField(llmOutput, "Category", "General"))
                .affectedSystems(Arrays.asList(extractField(llmOutput, "Affected systems", "Unknown").split(",")))
                .rootCauseHypothesis(extractField(llmOutput, "Root cause", "Under investigation"))
                .recommendedActions(Arrays.asList(extractField(llmOutput, "Recommended actions", "Escalate to team lead").split("\n")))
                .relevantPolicies(state.getRetrievedPolicies().stream()
                        .map(p -> p.getTitle() + " v" + p.getVersion())
                        .collect(Collectors.toList()))
                .escalationPath(extractField(llmOutput, "Escalation", "L2 Support"))
                .requiresHumanReview(!state.getGuardrailFlags().isEmpty())
                .build();
    }

    private String extractField(String text, String fieldName, String defaultValue) {
        String lower = text.toLowerCase();
        String fieldLower = fieldName.toLowerCase();
        int idx = lower.indexOf(fieldLower);
        if (idx < 0) return defaultValue;

        int start = text.indexOf(":", idx);
        if (start < 0) return defaultValue;
        start++;

        int end = text.indexOf("\n", start);
        if (end < 0) end = text.length();

        String value = text.substring(start, end).trim();
        return value.isEmpty() ? defaultValue : value;
    }

    private TriageTicket.Severity parseSeverity(String text) {
        String upper = text.toUpperCase();
        if (upper.contains("CRITICAL")) return TriageTicket.Severity.CRITICAL;
        if (upper.contains("HIGH")) return TriageTicket.Severity.HIGH;
        if (upper.contains("MEDIUM")) return TriageTicket.Severity.MEDIUM;
        return TriageTicket.Severity.LOW;
    }
}
