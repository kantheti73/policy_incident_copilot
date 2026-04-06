package com.copilot.guardrails;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reviews requests and responses for safety violations —
 * disallowed advice, policy circumvention attempts, and dangerous recommendations.
 */
@Slf4j
@Component
public class SafetyReviewer {

    private static final List<SafetyRule> RULES = List.of(
            new SafetyRule(
                    Pattern.compile("(?i)(disable|turn\\s+off|remove)\\s+(mfa|multi.?factor|two.?factor|2fa)"),
                    "DISABLE_MFA",
                    "Request to disable multi-factor authentication detected. This requires security team approval."),
            new SafetyRule(
                    Pattern.compile("(?i)(ignore|bypass|skip|circumvent)\\s+(policy|policies|compliance|audit)"),
                    "POLICY_BYPASS",
                    "Request to bypass company policy detected. Cannot provide guidance to circumvent policies."),
            new SafetyRule(
                    Pattern.compile("(?i)(share|send|expose|leak)\\s+(password|credential|secret|api.?key|token)"),
                    "CREDENTIAL_EXPOSURE",
                    "Request involves sharing credentials. Redirecting to secure credential management."),
            new SafetyRule(
                    Pattern.compile("(?i)(delete|drop|truncate|destroy)\\s+(all|database|table|production|prod)"),
                    "DESTRUCTIVE_ACTION",
                    "Destructive action detected. This requires change management approval."),
            new SafetyRule(
                    Pattern.compile("(?i)(grant|give|elevate)\\s+(admin|root|superuser|privileged)\\s+(access|permission|role)"),
                    "PRIVILEGE_ESCALATION",
                    "Privilege escalation request detected. This requires security team review.")
    );

    /**
     * Pre-check: evaluate input for safety concerns before processing.
     */
    public SafetyResult preCheck(String input) {
        List<String> flags = new ArrayList<>();

        for (SafetyRule rule : RULES) {
            if (rule.pattern.matcher(input).find()) {
                flags.add(rule.ruleId + ": " + rule.description);
                log.warn("Safety rule triggered: {}", rule.ruleId);
            }
        }

        return SafetyResult.builder()
                .safe(flags.isEmpty())
                .flags(flags)
                .build();
    }

    /**
     * Post-check: verify the generated response doesn't contain unsafe recommendations.
     */
    public SafetyResult postCheck(String response) {
        List<String> flags = new ArrayList<>();

        // Check if response recommends disabling security measures
        if (Pattern.compile("(?i)(recommend|suggest|should|advise).{0,30}(disable|remove|turn off).{0,30}(security|mfa|firewall|encryption)")
                .matcher(response).find()) {
            flags.add("UNSAFE_RECOMMENDATION: Response suggests disabling security controls");
        }

        // Check if response leaks what appears to be real credentials
        if (Pattern.compile("(?i)(password|secret|token)\\s*[:=]\\s*\\S{8,}")
                .matcher(response).find()) {
            flags.add("CREDENTIAL_LEAK: Response may contain actual credentials");
        }

        return SafetyResult.builder()
                .safe(flags.isEmpty())
                .flags(flags)
                .build();
    }

    @Data
    @Builder
    public static class SafetyResult {
        private boolean safe;
        @Builder.Default
        private List<String> flags = new ArrayList<>();
    }

    @AllArgsConstructor
    private static class SafetyRule {
        Pattern pattern;
        String ruleId;
        String description;
    }
}
