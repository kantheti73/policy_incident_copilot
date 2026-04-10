package com.copilot.config;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * Configures ChatLanguageModel beans for the copilot.
 *
 * Two beans are exposed:
 * - The default (primary) model — used by heavy-reasoning agents (Researcher, Orchestrator, Critic,
 *   PolicyQuery). Driven by ANTHROPIC_API_KEY or OPENAI_API_KEY.
 * - "plannerModel" (qualified) — a lightweight local Ollama model used by the PlannerAgent for
 *   fast, structured task decomposition. Falls back to the primary model if Ollama is unreachable
 *   or the user explicitly disables it.
 */
@Slf4j
@Configuration
public class ChatModelConfig {

    @Value("${copilot.llm.anthropic.api-key:}")
    private String anthropicApiKey;

    @Value("${copilot.llm.anthropic.model-name:claude-sonnet-4-20250514}")
    private String anthropicModel;

    @Value("${copilot.llm.openai.api-key:}")
    private String openaiApiKey;

    @Value("${copilot.llm.openai.model-name:gpt-4o}")
    private String openaiModel;

    @Value("${copilot.llm.max-tokens:4096}")
    private int maxTokens;

    @Value("${copilot.llm.temperature:0.1}")
    private double temperature;

    @Value("${copilot.llm.planner.enabled:true}")
    private boolean plannerLocalEnabled;

    @Value("${copilot.llm.planner.base-url:http://ollama:11434}")
    private String ollamaBaseUrl;

    @Value("${copilot.llm.planner.model-name:phi3:mini}")
    private String plannerModelName;

    @Bean
    @Primary
    public ChatLanguageModel chatLanguageModel() {
        if (isPresent(anthropicApiKey)) {
            log.info("Using Anthropic model for primary chat: {}", anthropicModel);
            return AnthropicChatModel.builder()
                    .apiKey(anthropicApiKey)
                    .modelName(anthropicModel)
                    .maxTokens(maxTokens)
                    .temperature(temperature)
                    .build();
        }

        if (isPresent(openaiApiKey)) {
            log.info("Using OpenAI model for primary chat: {}", openaiModel);
            return OpenAiChatModel.builder()
                    .apiKey(openaiApiKey)
                    .modelName(openaiModel)
                    .maxTokens(maxTokens)
                    .temperature(temperature)
                    .build();
        }

        throw new IllegalStateException(
                "No LLM API key configured. Set either ANTHROPIC_API_KEY or OPENAI_API_KEY in your .env file.");
    }

    /**
     * Lightweight model for the PlannerAgent. Uses local Ollama when available for low latency
     * and zero cost. Falls back to the primary chat model if Ollama is disabled.
     */
    @Bean
    @Qualifier("plannerModel")
    public ChatLanguageModel plannerModel(ChatLanguageModel primaryModel) {
        if (!plannerLocalEnabled) {
            log.info("Local planner model disabled; PlannerAgent will reuse the primary chat model");
            return primaryModel;
        }

        try {
            log.info("Configuring local Ollama planner model: {} at {}", plannerModelName, ollamaBaseUrl);
            return OllamaChatModel.builder()
                    .baseUrl(ollamaBaseUrl)
                    .modelName(plannerModelName)
                    .temperature(temperature)
                    .timeout(Duration.ofSeconds(60))
                    .build();
        } catch (Exception e) {
            log.warn("Failed to configure Ollama planner model ({}): {}. Falling back to primary model.",
                    plannerModelName, e.getMessage());
            return primaryModel;
        }
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
