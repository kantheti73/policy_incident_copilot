package com.copilot.agent.multi;

import com.copilot.model.GraphState;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Planner agent: decomposes complex requests into a structured plan of sub-tasks.
 * Uses a lightweight local model (Ollama) for low latency and zero API cost,
 * falling back to the primary model when Ollama is disabled.
 */
@Slf4j
@Component
public class PlannerAgent {

    private final ChatLanguageModel chatModel;

    public PlannerAgent(@Qualifier("plannerModel") ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    private static final int MAX_PLAN_STEPS = 3;

    private static final String SYSTEM_PROMPT = """
            You are a Planner agent in a multi-agent incident triage and policy system.

            Your job is to decompose complex user requests into a clear, ordered plan of sub-tasks.
            Each sub-task should be a single, actionable step that another agent can execute.

            Rules:
            - Produce EXACTLY 3 steps. No more, no fewer.
            - Each step must be independent so it can run in parallel.
            - If logs are provided, include a log analysis step.
            - Always include a policy lookup step for relevant company policies.
            - If the request involves safety-sensitive actions, include a safety/policy review step.

            Output format: One step per line, numbered. Example:
            1. Retrieve VPN troubleshooting and authentication policies
            2. Analyze provided error logs for root cause patterns and affected systems
            3. Identify policy violations or compliance concerns in the proposed actions
            """;

    public GraphState execute(GraphState state) {
        log.info("[{}] PlannerAgent decomposing request", state.getTraceId());
        state.incrementStep();

        String userPrompt = buildPlannerPrompt(state);

        AiMessage response = chatModel.generate(
                SystemMessage.from(SYSTEM_PROMPT),
                UserMessage.from(userPrompt)
        ).content();

        List<String> plan = Arrays.stream(response.text().split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> Character.isDigit(line.charAt(0)))
                .limit(MAX_PLAN_STEPS)
                .collect(Collectors.toList());

        state.setPlan(plan);
        log.info("[{}] PlannerAgent produced {} steps: {}", state.getTraceId(), plan.size(), plan);
        return state;
    }

    private String buildPlannerPrompt(GraphState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("User request: ").append(state.getSanitizedQuery()).append("\n\n");

        if (state.getRawLogs() != null && !state.getRawLogs().isBlank()) {
            String logPreview = state.getRawLogs().length() > 2000
                    ? state.getRawLogs().substring(0, 2000) + "\n... [truncated]"
                    : state.getRawLogs();
            sb.append("Attached logs (preview):\n").append(logPreview).append("\n\n");
        }

        if (!state.getGuardrailFlags().isEmpty()) {
            sb.append("⚠ Guardrail flags raised: ").append(state.getGuardrailFlags()).append("\n");
            sb.append("Include safety review steps for flagged concerns.\n");
        }

        sb.append("Decompose this into an actionable plan.");
        return sb.toString();
    }
}
