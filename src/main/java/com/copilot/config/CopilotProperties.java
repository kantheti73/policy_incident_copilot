package com.copilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "copilot")
public class CopilotProperties {

    private Routing routing = new Routing();
    private Guardrails guardrails = new Guardrails();
    private Memory memory = new Memory();
    private Retry retry = new Retry();
    private LlmPricing llmPricing = new LlmPricing();

    @Data
    public static class Routing {
        private double complexityThreshold = 0.6;
        private List<String> highRiskKeywords;
    }

    @Data
    public static class Guardrails {
        private int maxInputLength = 50000;
        private int maxStepsSingleAgent = 3;
        private int maxStepsMultiAgent = 10;
        private List<String> piiPatterns;
    }

    @Data
    public static class Memory {
        private int maxConversationTurns = 20;
        private List<String> sensitiveKeywords;
    }

    @Data
    public static class Retry {
        private int maxAttempts = 3;
        private int backoffMs = 1000;
    }

    @Data
    public static class LlmPricing {
        /** Map of model name → pricing. Use lower-case keys; lookup is case-insensitive. */
        private Map<String, ModelPrice> models = new HashMap<>();
    }

    @Data
    public static class ModelPrice {
        /** USD per 1 million input tokens. */
        private double inputPerMillion = 0.0;
        /** USD per 1 million output tokens. */
        private double outputPerMillion = 0.0;
    }
}
