package com.copilot.guardrails;

import com.copilot.model.GraphState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Central guardrails engine that orchestrates input and output safety checks.
 * Applied at graph boundaries (before and after agent processing).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuardrailsEngine {

    private final PromptInjectionDetector injectionDetector;
    private final PIIRedactor piiRedactor;
    private final SafetyReviewer safetyReviewer;

    /**
     * Input guardrails: sanitize, detect injection, redact PII before agent processing.
     */
    public GraphState applyInputGuardrails(GraphState state) {
        log.info("[{}] Applying input guardrails", state.getTraceId());

        // Step 1: PII Redaction on input
        String sanitizedQuery = piiRedactor.redact(state.getOriginalQuery());
        state.setSanitizedQuery(sanitizedQuery);

        if (state.getRawLogs() != null) {
            state.setRawLogs(piiRedactor.redact(state.getRawLogs()));
        }

        // Step 2: Prompt injection detection
        PromptInjectionDetector.DetectionResult injectionResult =
                injectionDetector.detect(state.getOriginalQuery());

        if (injectionResult.isInjectionDetected()) {
            state.getGuardrailFlags().add("PROMPT_INJECTION_DETECTED: " + injectionResult.getPattern());
            log.warn("[{}] Prompt injection detected: {}", state.getTraceId(), injectionResult.getPattern());

            if (injectionResult.getSeverity() == PromptInjectionDetector.Severity.CRITICAL) {
                state.setError("Request blocked: suspected prompt injection attempt.");
                state.setFinalResponse("I'm unable to process this request as it appears to contain "
                        + "instructions that could compromise system safety. Please rephrase your question.");
                return state;
            }
        }

        // Step 3: Safety pre-check for disallowed requests
        SafetyReviewer.SafetyResult safetyResult = safetyReviewer.preCheck(sanitizedQuery);
        if (!safetyResult.isSafe()) {
            state.getGuardrailFlags().addAll(safetyResult.getFlags());
            log.warn("[{}] Safety pre-check flags: {}", state.getTraceId(), safetyResult.getFlags());
        }

        log.info("[{}] Input guardrails complete. Flags: {}", state.getTraceId(), state.getGuardrailFlags());
        return state;
    }

    /**
     * Output guardrails: redact PII from response, final safety check.
     */
    public GraphState applyOutputGuardrails(GraphState state) {
        log.info("[{}] Applying output guardrails", state.getTraceId());

        if (state.getFinalResponse() != null) {
            // Redact any PII that might have leaked into the response
            state.setFinalResponse(piiRedactor.redact(state.getFinalResponse()));
        }

        log.info("[{}] Output guardrails complete", state.getTraceId());
        return state;
    }
}
