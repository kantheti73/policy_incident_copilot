package com.copilot.memory;

import com.copilot.config.CopilotProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Filters sensitive content before it is persisted to conversation memory.
 * Ensures passwords, tokens, API keys, and other secrets are never stored.
 */
@Slf4j
@Component
public class SafeMemoryFilter {

    private final List<String> sensitiveKeywords;

    private static final Pattern CREDENTIAL_PATTERN = Pattern.compile(
            "(?i)(password|passwd|secret|token|api[_-]?key|credential|bearer)\\s*[:=]\\s*\\S+");

    private static final Pattern BASE64_SECRET_PATTERN = Pattern.compile(
            "(?i)(key|secret|token)\\s*[:=]\\s*[A-Za-z0-9+/=]{20,}");

    public SafeMemoryFilter(CopilotProperties properties) {
        this.sensitiveKeywords = properties.getMemory().getSensitiveKeywords();
    }

    /**
     * Filter sensitive content from text before storing in memory.
     * Returns sanitized text safe for persistence.
     */
    public String filter(String text) {
        if (text == null) return null;

        String filtered = text;

        // Replace credential patterns
        filtered = CREDENTIAL_PATTERN.matcher(filtered).replaceAll("[SENSITIVE_CONTENT_REMOVED]");
        filtered = BASE64_SECRET_PATTERN.matcher(filtered).replaceAll("[SENSITIVE_CONTENT_REMOVED]");

        // Check for sensitive keyword proximity (keyword near a value)
        for (String keyword : sensitiveKeywords) {
            Pattern keywordPattern = Pattern.compile(
                    "(?i)" + Pattern.quote(keyword) + "\\s*[:=]\\s*\\S+");
            filtered = keywordPattern.matcher(filtered).replaceAll("[SENSITIVE_CONTENT_REMOVED]");
        }

        if (!filtered.equals(text)) {
            log.info("Sensitive content filtered from memory entry");
        }

        return filtered;
    }

    /**
     * Check if text contains sensitive content that should not be persisted.
     */
    public boolean containsSensitiveContent(String text) {
        if (text == null) return false;
        return CREDENTIAL_PATTERN.matcher(text).find()
                || BASE64_SECRET_PATTERN.matcher(text).find();
    }
}
