package com.copilot.observability;

import com.copilot.model.GraphState;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.DistributionSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Collects and publishes Prometheus-compatible metrics for the copilot system.
 */
@Slf4j
@Component
public class MetricsCollector {

    private final MeterRegistry registry;
    private final Counter requestsTotal;
    private final Counter singleAgentRequests;
    private final Counter multiAgentRequests;
    private final Counter guardrailTriggered;
    private final Counter promptInjectionBlocked;
    private final Counter piiRedacted;
    private final Counter errorsTotal;
    private final Timer requestLatency;
    private final DistributionSummary stepsPerRequest;

    // Cached per-model token-distribution summaries to avoid re-creating on each call.
    private final ConcurrentMap<String, DistributionSummary> tokensPerCallSummaries = new ConcurrentHashMap<>();

    public MetricsCollector(MeterRegistry registry) {
        this.registry = registry;
        this.requestsTotal = Counter.builder("copilot.requests.total")
                .description("Total copilot requests")
                .register(registry);

        this.singleAgentRequests = Counter.builder("copilot.requests.single_agent")
                .description("Single-agent mode requests")
                .register(registry);

        this.multiAgentRequests = Counter.builder("copilot.requests.multi_agent")
                .description("Multi-agent mode requests")
                .register(registry);

        this.guardrailTriggered = Counter.builder("copilot.guardrails.triggered")
                .description("Guardrail flags raised")
                .register(registry);

        this.promptInjectionBlocked = Counter.builder("copilot.guardrails.injection_blocked")
                .description("Prompt injection attempts blocked")
                .register(registry);

        this.piiRedacted = Counter.builder("copilot.guardrails.pii_redacted")
                .description("PII redaction events")
                .register(registry);

        this.errorsTotal = Counter.builder("copilot.errors.total")
                .description("Total errors")
                .register(registry);

        this.requestLatency = Timer.builder("copilot.request.latency")
                .description("Request processing latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.stepsPerRequest = DistributionSummary.builder("copilot.steps.per_request")
                .description("Number of agent steps per request")
                .register(registry);
    }

    public void recordRequest(GraphState state, long latencyMs) {
        requestsTotal.increment();

        if (state.getRoutingDecision() == GraphState.RoutingDecision.SINGLE) {
            singleAgentRequests.increment();
        } else {
            multiAgentRequests.increment();
        }

        requestLatency.record(Duration.ofMillis(latencyMs));
        stepsPerRequest.record(state.getCurrentStep());

        if (!state.getGuardrailFlags().isEmpty()) {
            guardrailTriggered.increment(state.getGuardrailFlags().size());
        }

        log.debug("Metrics recorded: mode={}, steps={}, latency={}ms, flags={}",
                state.getRoutingDecision(), state.getCurrentStep(),
                latencyMs, state.getGuardrailFlags().size());
    }

    public void recordPromptInjectionBlocked() {
        promptInjectionBlocked.increment();
    }

    public void recordPIIRedaction() {
        piiRedacted.increment();
    }

    public void recordError() {
        errorsTotal.increment();
    }

    /**
     * Record token usage from a single LLM call. Tags by model and provider so dashboards can
     * break down spend, throughput, and call counts per model.
     */
    public void recordTokenUsage(String provider, String model, int inputTokens, int outputTokens, double costUsd) {
        registry.counter("copilot.llm.tokens.input", "model", model, "provider", provider).increment(inputTokens);
        registry.counter("copilot.llm.tokens.output", "model", model, "provider", provider).increment(outputTokens);
        registry.counter("copilot.llm.tokens.total", "model", model, "provider", provider).increment(inputTokens + outputTokens);
        registry.counter("copilot.llm.calls.total", "model", model, "provider", provider).increment();

        if (costUsd > 0.0) {
            registry.counter("copilot.llm.cost.usd", "model", model, "provider", provider).increment(costUsd);
        }

        tokensPerCallSummaries
                .computeIfAbsent(model, m -> DistributionSummary.builder("copilot.llm.tokens.per_call")
                        .description("Tokens consumed per LLM call (input + output)")
                        .tag("model", model)
                        .tag("provider", provider)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(registry))
                .record(inputTokens + outputTokens);

        log.debug("LLM tokens recorded: model={}, in={}, out={}, cost=${}",
                model, inputTokens, outputTokens, String.format("%.5f", costUsd));
    }
}
