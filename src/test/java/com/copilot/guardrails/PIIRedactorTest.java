package com.copilot.guardrails;

import com.copilot.config.CopilotProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PIIRedactorTest {

    private PIIRedactor redactor;

    @BeforeEach
    void setUp() {
        List<String> patterns = List.of(
                "\\b\\d{3}-\\d{2}-\\d{4}\\b",                             // SSN
                "\\b\\d{16}\\b",                                          // Credit card
                "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z]{2,}\\b",  // Email
                "\\b\\d{3}[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b"             // Phone
        );
        CopilotProperties props = new CopilotProperties();
        CopilotProperties.Guardrails guardrails = new CopilotProperties.Guardrails();
        guardrails.setPiiPatterns(patterns);
        props.setGuardrails(guardrails);
        redactor = new PIIRedactor(props);
    }

    @Test
    void shouldRedactSSN() {
        String input = "User SSN is 123-45-6789 and needs help";
        String result = redactor.redact(input);
        assertFalse(result.contains("123-45-6789"));
        assertTrue(result.contains("[REDACTED]"));
    }

    @Test
    void shouldRedactPhoneNumbers() {
        String input = "Call me at 555-123-4567 for follow-up";
        String result = redactor.redact(input);
        assertFalse(result.contains("555-123-4567"));
        assertTrue(result.contains("[REDACTED]"));
    }

    @Test
    void shouldNotRedactCleanText() {
        String input = "What is the password reset policy?";
        String result = redactor.redact(input);
        assertEquals(input, result);
    }

    @Test
    void shouldDetectPIIPresence() {
        assertTrue(redactor.containsPII("SSN: 123-45-6789"));
        assertFalse(redactor.containsPII("Normal text without PII"));
    }

    @Test
    void shouldHandleNullInput() {
        assertNull(redactor.redact(null));
    }
}
