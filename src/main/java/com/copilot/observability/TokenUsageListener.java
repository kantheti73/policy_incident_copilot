package com.copilot.observability;

import com.copilot.config.CopilotProperties;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LangChain4J chat model listener that captures token usage on every LLM call
 * and forwards it to MetricsCollector. Attached to all ChatLanguageModel beans
 * in ChatModelConfig so we get coverage across Anthropic, OpenAI, and Ollama
 * without modifying any agent code.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenUsageListener implements ChatModelListener {

    private final MetricsCollector metricsCollector;
    private final CopilotProperties properties;

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        // No-op — we record on response when token counts are known.
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        try {
            String model = responseContext.response().model();
            TokenUsage usage = responseContext.response().tokenUsage();

            if (usage == null) {
                log.debug("LLM response had no token usage info (model={})", model);
                return;
            }

            int inputTokens = usage.inputTokenCount() != null ? usage.inputTokenCount() : 0;
            int outputTokens = usage.outputTokenCount() != null ? usage.outputTokenCount() : 0;

            String provider = inferProvider(model);
            double costUsd = computeCost(model, inputTokens, outputTokens);

            metricsCollector.recordTokenUsage(provider, model, inputTokens, outputTokens, costUsd);
        } catch (Exception e) {
            log.warn("Failed to record token usage: {}", e.getMessage());
        }
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        // No-op — errors are tracked separately via MetricsCollector.recordError().
    }

    private String inferProvider(String model) {
        if (model == null) return "unknown";
        String lower = model.toLowerCase();
        if (lower.contains("claude")) return "anthropic";
        if (lower.startsWith("gpt") || lower.contains("o1") || lower.contains("o3")) return "openai";
        if (lower.contains("phi") || lower.contains("llama") || lower.contains("qwen")
                || lower.contains("gemma") || lower.contains("mistral") || lower.contains("granite")) {
            return "ollama";
        }
        return "unknown";
    }

    private double computeCost(String model, int inputTokens, int outputTokens) {
        if (model == null) return 0.0;
        var pricing = properties.getLlmPricing().getModels();
        var price = pricing.get(model.toLowerCase());
        if (price == null) {
            return 0.0;
        }
        return (inputTokens / 1_000_000.0) * price.getInputPerMillion()
             + (outputTokens / 1_000_000.0) * price.getOutputPerMillion();
    }
}
