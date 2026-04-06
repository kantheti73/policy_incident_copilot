package com.copilot.guardrails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SafetyReviewerTest {

    private SafetyReviewer safetyReviewer;

    @BeforeEach
    void setUp() {
        safetyReviewer = new SafetyReviewer();
    }

    @Test
    void shouldFlagMFADisableRequest() {
        var result = safetyReviewer.preCheck("Please disable MFA for all users to unblock them");
        assertFalse(result.isSafe());
        assertTrue(result.getFlags().stream().anyMatch(f -> f.contains("DISABLE_MFA")));
    }

    @Test
    void shouldFlagPolicyBypass() {
        var result = safetyReviewer.preCheck("Can we bypass the compliance policy for this release?");
        assertFalse(result.isSafe());
        assertTrue(result.getFlags().stream().anyMatch(f -> f.contains("POLICY_BYPASS")));
    }

    @Test
    void shouldFlagPrivilegeEscalation() {
        var result = safetyReviewer.preCheck("Grant admin access to the new contractor");
        assertFalse(result.isSafe());
        assertTrue(result.getFlags().stream().anyMatch(f -> f.contains("PRIVILEGE_ESCALATION")));
    }

    @Test
    void shouldAllowLegitimateQueries() {
        var result = safetyReviewer.preCheck("What is the process for resetting a password?");
        assertTrue(result.isSafe());
        assertTrue(result.getFlags().isEmpty());
    }

    @Test
    void shouldFlagUnsafeOutputRecommendations() {
        var result = safetyReviewer.postCheck(
                "I recommend you disable the firewall to resolve this issue");
        assertFalse(result.isSafe());
    }
}
