package com.copilot.agent.multi;

import com.copilot.model.GraphState;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Planner agent: decomposes complex requests into a structured plan of sub-tasks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlannerAgent {

    private final ChatLanguageModel chatModel;

    private static final String SYSTEM_PROMPT = """
            You are a Planner agent in a multi-agent incident triage and policy system.

            Your job is to decompose complex user requests into a clear, ordered plan of sub-tasks.
            Each sub-task should be a single, actionable step that another agent can execute.

            Rules:
            - If the request involves safety-sensitive actions (disabling security, overriding policies),
              include a mandatory safety review step.
            - If logs are provided, include a log analysis step.
            - Always include a policy lookup step for relevant company policies.
            - Always end with a verification/review step.
            - Keep the plan to 3-7 steps maximum.

            Output format: One step per line, numbered. Example:
            1. Retrieve VPN troubleshooting policies
            2. Analyze provided error logs for root cause patterns
            3. Cross-reference findings with known incident database
            4. Draft incident triage ticket with severity assessment
            5. Safety review: verify no policy violations in recommended actions
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
