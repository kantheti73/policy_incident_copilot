package com.copilot.guardrails;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects prompt injection attempts in user input using pattern matching
 * and heuristic analysis.
 */
@Slf4j
@Component
public class PromptInjectionDetector {

    private static final List<InjectionPattern> PATTERNS = List.of(
            new InjectionPattern(
                    Pattern.compile("(?i)(ignore|forget|disregard)\\s+(all\\s+)?(previous|above|prior)\\s+(instructions|prompts|rules)"),
                    "INSTRUCTION_OVERRIDE", Severity.CRITICAL),
            new InjectionPattern(
                    Pattern.compile("(?i)you\\s+are\\s+now\\s+(a|an|the)"),
                    "ROLE_HIJACK", Severity.CRITICAL),
            new InjectionPattern(
                    Pattern.compile("(?i)(system\\s*prompt|hidden\\s*instruction|reveal\\s*(your|the)\\s*(prompt|instructions))"),
                    "PROMPT_EXTRACTION", Severity.HIGH),
            new InjectionPattern(
                    Pattern.compile("(?i)(pretend|act\\s+as\\s+if|imagine\\s+you)"),
                    "ROLE_PLAY_INJECTION", Severity.MEDIUM),
            new InjectionPattern(
                    Pattern.compile("(?i)(do\\s+not\\s+follow|override\\s+(safety|security|guardrail))"),
                    "SAFETY_OVERRIDE", Severity.CRITICAL),
            new InjectionPattern(
                    Pattern.compile("(?i)\\[\\s*(SYSTEM|INST|ADMIN)\\s*\\]"),
                    "FAKE_SYSTEM_TAG", Severity.CRITICAL),
            new InjectionPattern(
                    Pattern.compile("(?i)(jailbreak|DAN|do\\s+anything\\s+now)"),
                    "KNOWN_JAILBREAK", Severity.CRITICAL)
    );

    public DetectionResult detect(String input) {
        if (input == null || input.isBlank()) {
            return DetectionResult.safe();
        }

        for (InjectionPattern pattern : PATTERNS) {
            if (pattern.regex.matcher(input).find()) {
                log.warn("Prompt injection pattern matched: {} (severity: {})",
                        pattern.name, pattern.severity);
                return DetectionResult.builder()
                        .injectionDetected(true)
                        .pattern(pattern.name)
                        .severity(pattern.severity)
                        .build();
            }
        }

        // Heuristic: unusually high ratio of instruction-like language
        long instructionWords = countInstructionWords(input);
        if (instructionWords > 5 && (double) instructionWords / input.split("\\s+").length > 0.3) {
            return DetectionResult.builder()
                    .injectionDetected(true)
                    .pattern("HIGH_INSTRUCTION_DENSITY")
                    .severity(Severity.MEDIUM)
                    .build();
        }

        return DetectionResult.safe();
    }

    private long countInstructionWords(String input) {
        String lower = input.toLowerCase();
        String[] instructionTerms = {"must", "always", "never", "output", "respond", "format",
                "instructions", "override", "bypass", "ignore"};
        long count = 0;
        for (String term : instructionTerms) {
            if (lower.contains(term)) count++;
        }
        return count;
    }

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    @Data
    @Builder
    public static class DetectionResult {
        private boolean injectionDetected;
        private String pattern;
        private Severity severity;

        public static DetectionResult safe() {
            return DetectionResult.builder()
                    .injectionDetected(false)
                    .severity(Severity.LOW)
                    .build();
        }
    }

    @AllArgsConstructor
    private static class InjectionPattern {
        Pattern regex;
        String name;
        Severity severity;
    }
}
