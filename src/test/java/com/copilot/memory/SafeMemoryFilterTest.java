package com.copilot.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SafeMemoryFilterTest {

    private SafeMemoryFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SafeMemoryFilter(List.of("password", "secret", "token", "api-key", "credential"));
    }

    @Test
    void shouldFilterPasswordValues() {
        String input = "My password: SuperSecret123! needs to be reset";
        String result = filter.filter(input);
        assertFalse(result.contains("SuperSecret123!"));
        assertTrue(result.contains("[SENSITIVE_CONTENT_REMOVED]"));
    }

    @Test
    void shouldFilterApiKeys() {
        String input = "Set api-key=sk_live_abc123def456 in the config";
        String result = filter.filter(input);
        assertFalse(result.contains("sk_live_abc123def456"));
    }

    @Test
    void shouldNotFilterNormalText() {
        String input = "What is the password reset policy?";
        String result = filter.filter(input);
        assertEquals(input, result);
    }

    @Test
    void shouldDetectSensitiveContent() {
        assertTrue(filter.containsSensitiveContent("password=MyP@ss123"));
        assertFalse(filter.containsSensitiveContent("How do I change my password?"));
    }

    @Test
    void shouldHandleNull() {
        assertNull(filter.filter(null));
    }
}
