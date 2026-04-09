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
                    Pattern.compile("(?i)(disable|turn\\s+off|remove|stop|deactivate)\\s+(mfa|multi.?factor|two.?factor|2fa)"),
                    "DISABLE_MFA",
                    "Request to disable multi-factor authentication detected. This requires security team approval."),
            new SafetyRule(
                    Pattern.compile("(?i)(disable|turn\\s+off|remove|stop|deactivate|open\\s+all\\s+ports\\s+on)\\s+(the\\s+)?(firewall|iptables|windows\\s+defender\\s+firewall|ufw|network\\s+filter)"),
                    "DISABLE_FIREWALL",
                    "Request to disable firewall detected. Firewall changes require network security team approval."),
            new SafetyRule(
                    Pattern.compile("(?i)(ignore|bypass|skip|circumvent)\\s+(policy|policies|compliance|audit)"),
                    "POLICY_BYPASS",
                    "Request to bypass company policy detected. Cannot provide guidance to circumvent policies."),
            new SafetyRule(
                    Pattern.compile("(?i)(share|send|expose|leak)\\s+(password|credential|secret|api.?key|token)"),
                    "CREDENTIAL_EXPOSURE",
                    "Request involves sharing credentials. Redirecting to secure credential management."),
            new SafetyRule(
                    Pattern.compile("(?i)(what\\s+is|give\\s+me|tell\\s+me|provide|send|share|reveal|show)\\s+(the\\s+)?(password|credentials?|login|secret|api.?key|token|private.?key)\\s+(for|to|of)"),
                    "PASSWORD_REQUEST",
                    "Request for system passwords or credentials detected. Use the enterprise password vault for credential access."),
            new SafetyRule(
                    Pattern.compile("(?i)(install|download|run|execute|sideload)\\s+(this\\s+|an?\\s+)?(unverified|untrusted|unsigned|cracked|pirated|unknown|third.?party)\\s+(software|program|app|application|tool|exe|script|package)"),
                    "UNVERIFIED_SOFTWARE",
                    "Request to install unverified software detected. Only IT-approved software may be installed per company policy."),
            new SafetyRule(
                    Pattern.compile("(?i)(install|download|run|execute)\\s+.{0,50}(from\\s+)?(http[s]?://(?!\\S*\\b(github\\.com|microsoft\\.com|apache\\.org)\\b)\\S+)"),
                    "UNTRUSTED_DOWNLOAD",
                    "Request to install software from an unverified source detected. Software must be sourced from IT-approved repositories."),
            new SafetyRule(
                    Pattern.compile("(?i)(what\\s+is|give\\s+me|tell\\s+me|provide|list|show|reveal)\\s+(the\\s+)?(ip\\s+address|internal\\s+ip|server\\s+ip|network\\s+address|subnet|ip\\s+range|network\\s+map|network\\s+topology)\\s*(of|for)?"),
                    "NETWORK_INFO_REQUEST",
                    "Request for internal network information detected. Network details require infrastructure team authorization."),
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
        if (Pattern.compile("(?i)(recommend|suggest|should|advise|try|go\\s+ahead).{0,30}(disable|remove|turn off|deactivate|stop).{0,30}(security|mfa|firewall|encryption|antivirus|defender)")
                .matcher(response).find()) {
            flags.add("UNSAFE_RECOMMENDATION: Response suggests disabling security controls");
        }

        // Check if response leaks what appears to be real credentials
        if (Pattern.compile("(?i)(password|secret|token)\\s*[:=]\\s*\\S{8,}")
                .matcher(response).find()) {
            flags.add("CREDENTIAL_LEAK: Response may contain actual credentials");
        }

        // Check if response contains internal IP addresses
        if (Pattern.compile("\\b(10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|172\\.(1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3}|192\\.168\\.\\d{1,3}\\.\\d{1,3})\\b")
                .matcher(response).find()) {
            flags.add("NETWORK_INFO_LEAK: Response contains internal IP addresses");
        }

        // Check if response recommends installing from untrusted sources
        if (Pattern.compile("(?i)(download|install).{0,30}(from|at|via)\\s+http")
                .matcher(response).find()) {
            flags.add("UNTRUSTED_SOURCE: Response suggests downloading software from an external URL");
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
