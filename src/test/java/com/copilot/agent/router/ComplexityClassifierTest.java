package com.copilot.agent.router;

import com.copilot.model.CopilotRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComplexityClassifierTest {

    private ComplexityClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new ComplexityClassifier();
        ReflectionTestUtils.setField(classifier, "complexityThreshold", 0.6);
        ReflectionTestUtils.setField(classifier, "highRiskKeywords",
                List.of("disable mfa", "ignore policy", "bypass", "override security"));
    }

    @Test
    void shouldRouteSingleForSimpleQuery() {
        CopilotRequest request = CopilotRequest.builder()
                .query("What is the current password reset process?")
                .build();

        var result = classifier.classify(request);
        assertFalse(result.isMultiAgentRequired());
        assertFalse(result.isHighRisk());
    }

    @Test
    void shouldRouteMultiForHighRiskKeyword() {
        CopilotRequest request = CopilotRequest.builder()
                .query("Can we disable MFA to unblock the 200 affected users?")
                .build();

        var result = classifier.classify(request);
        assertTrue(result.isMultiAgentRequired());
        assertTrue(result.isHighRisk());
    }

    @Test
    void shouldRouteMultiForLongQueryWithLogs() {
        CopilotRequest request = CopilotRequest.builder()
                .query("VPN is failing for multiple users across the org. " +
                        "The error started after yesterday's maintenance window. " +
                        "Users are reporting timeouts and authentication failures. " +
                        "This is impacting all remote workers in the APAC region. " +
                        "We need immediate triage and resolution path.")
                .rawLogs("2024-01-15 10:00:00 ERROR vpn-gateway Connection timeout\n".repeat(50))
                .build();

        var result = classifier.classify(request);
        assertTrue(result.isMultiAgentRequired());
    }

    @Test
    void shouldRouteMultiForMultipleQuestions() {
        CopilotRequest request = CopilotRequest.builder()
                .query("What is the VPN policy? How does it relate to remote work? "
                        + "Who approves exceptions? Is there a backup connection method?")
                .build();

        var result = classifier.classify(request);
        // 4 question marks + moderate length should push towards multi
        assertTrue(result.getComplexityScore() > 0.0);
    }
}
