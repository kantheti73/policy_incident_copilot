package com.copilot.config;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the ChatLanguageModel bean based on which API key is provided.
 * Set ANTHROPIC_API_KEY to use Anthropic (Claude) or OPENAI_API_KEY to use OpenAI (GPT).
 * If both are set, Anthropic takes precedence.
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

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        if (isPresent(anthropicApiKey)) {
            log.info("Using Anthropic model: {}", anthropicModel);
            return AnthropicChatModel.builder()
                    .apiKey(anthropicApiKey)
                    .modelName(anthropicModel)
                    .maxTokens(maxTokens)
                    .temperature(temperature)
                    .build();
        }

        if (isPresent(openaiApiKey)) {
            log.info("Using OpenAI model: {}", openaiModel);
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

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
