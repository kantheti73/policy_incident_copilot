package com.copilot.agent.multi;

import com.copilot.model.GraphState;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Critic/Verifier agent: reviews the draft response for accuracy, policy compliance,
 * and safety before final delivery.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CriticAgent {

    private final ChatLanguageModel chatModel;

    private static final String SYSTEM_PROMPT = """
            You are a Critic/Verifier agent in a multi-agent policy and incident triage system.

            Your job is to review a draft response and ensure:
            1. ACCURACY: All cited policies exist in the provided context and are correctly referenced.
            2. COMPLETENESS: The response addresses all parts of the user's request.
            3. SAFETY: The response does not recommend actions that violate company policies,
               disable security controls without proper authorization, or expose sensitive data.
            4. TONE: The response is professional, clear, and actionable.
            5. GUARDRAILS: If any guardrail flags were raised, verify they were properly addressed.

            Output format:
            VERDICT: APPROVE or REVISE
            ISSUES: (list any issues found, or "None")
            REVISED_RESPONSE: (if REVISE, provide the corrected response; if APPROVE, repeat the draft)
            """;

    public GraphState execute(GraphState state) {
        log.info("[{}] CriticAgent reviewing draft response", state.getTraceId());
        state.incrementStep();

        String findingsSummary = state.getResearchFindings().entrySet().stream()
                .map(e -> "Step: " + e.getKey() + "\nFindings: " + e.getValue())
                .collect(Collectors.joining("\n\n"));

        String reviewPrompt = String.format("""
                Original user request: %s

                Research findings:
                %s

                Draft response to review:
                %s

                Guardrail flags: %s

                Review the draft and provide your verdict.
                """,
                state.getOriginalQuery(),
                findingsSummary,
                state.getDraftResponse(),
                state.getGuardrailFlags());

        AiMessage response = chatModel.generate(
                SystemMessage.from(SYSTEM_PROMPT),
                UserMessage.from(reviewPrompt)
        ).content();

        String criticOutput = response.text();

        if (criticOutput.contains("VERDICT: APPROVE")) {
            state.setSafetyApproved(true);
            state.setFinalResponse(state.getDraftResponse());
            log.info("[{}] CriticAgent APPROVED the response", state.getTraceId());
        } else {
            // Extract revised response
            int revisedIdx = criticOutput.indexOf("REVISED_RESPONSE:");
            if (revisedIdx >= 0) {
                String revised = criticOutput.substring(revisedIdx + "REVISED_RESPONSE:".length()).trim();
                state.setFinalResponse(revised);
            } else {
                state.setFinalResponse(state.getDraftResponse());
            }
            state.setSafetyApproved(true);
            state.getGuardrailFlags().add("CRITIC_REVISED: Response was modified by safety review");
            log.warn("[{}] CriticAgent REVISED the response", state.getTraceId());
        }

        return state;
    }
}
