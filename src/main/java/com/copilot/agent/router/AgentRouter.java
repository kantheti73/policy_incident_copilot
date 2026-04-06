package com.copilot.agent.router;

import com.copilot.model.CopilotRequest;
import com.copilot.model.GraphState;
import com.copilot.observability.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Routes incoming requests to single-agent or multi-agent workflows
 * based on complexity classification and optional user override.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRouter {

    private final ComplexityClassifier complexityClassifier;

    public GraphState route(CopilotRequest request, TraceContext traceContext) {
        GraphState.RoutingDecision decision;

        if (request.getForceMode() != null) {
            decision = request.getForceMode() == CopilotRequest.AgentMode.MULTI
                    ? GraphState.RoutingDecision.MULTI
                    : GraphState.RoutingDecision.SINGLE;
            log.info("[{}] Forced routing to: {}", traceContext.getTraceId(), decision);
        } else {
            ComplexityClassifier.ClassificationResult result =
                    complexityClassifier.classify(request);
            decision = result.isMultiAgentRequired()
                    ? GraphState.RoutingDecision.MULTI
                    : GraphState.RoutingDecision.SINGLE;
            log.info("[{}] Auto-routed to: {} (score={}, highRisk={})",
                    traceContext.getTraceId(), decision,
                    result.getComplexityScore(), result.isHighRisk());
        }

        int maxSteps = decision == GraphState.RoutingDecision.SINGLE ? 3 : 10;

        return GraphState.builder()
                .traceId(traceContext.getTraceId())
                .sessionId(request.getSessionId())
                .originalQuery(request.getQuery())
                .rawLogs(request.getRawLogs())
                .routingDecision(decision)
                .maxSteps(maxSteps)
                .build();
    }
}
