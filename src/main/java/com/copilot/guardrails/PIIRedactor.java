package com.copilot.guardrails;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Redacts Personally Identifiable Information (PII) from text
 * using configurable regex patterns.
 */
@Slf4j
@Component
public class PIIRedactor {

    private final List<Pattern> piiPatterns;

    public PIIRedactor(@Value("${copilot.guardrails.pii-patterns}") List<String> patternStrings) {
        this.piiPatterns = patternStrings.stream()
                .map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE))
                .collect(Collectors.toList());
    }

    /**
     * Redact all PII matches in the input text with [REDACTED] placeholders.
     */
    public String redact(String text) {
        if (text == null || text.isBlank()) return text;

        String redacted = text;
        int totalRedactions = 0;

        for (Pattern pattern : piiPatterns) {
            var matcher = pattern.matcher(redacted);
            int count = 0;
            while (matcher.find()) count++;

            if (count > 0) {
                redacted = pattern.matcher(redacted).replaceAll("[REDACTED]");
                totalRedactions += count;
            }
        }

        if (totalRedactions > 0) {
            log.info("Redacted {} PII occurrences from text", totalRedactions);
        }

        return redacted;
    }

    /**
     * Check if text contains any PII without redacting.
     */
    public boolean containsPII(String text) {
        if (text == null) return false;
        return piiPatterns.stream().anyMatch(p -> p.matcher(text).find());
    }
}
