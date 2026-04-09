package com.copilot.service;

import com.copilot.agent.router.AgentRouter;
import com.copilot.agent.single.PolicyQueryAgent;
import com.copilot.graph.MultiAgentGraph;
import com.copilot.graph.SingleAgentGraph;
import com.copilot.memory.ConversationMemory;
import com.copilot.model.*;
import com.copilot.observability.MetricsCollector;
import com.copilot.observability.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Core service that orchestrates the copilot workflow:
 * routing → graph execution → response assembly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CopilotService {

    private final AgentRouter agentRouter;
    private final SingleAgentGraph singleAgentGraph;
    private final MultiAgentGraph multiAgentGraph;
    private final PolicyQueryAgent policyQueryAgent;
    private final ConversationMemory conversationMemory;
    private final MetricsCollector metricsCollector;

    public CopilotResponse process(CopilotRequest request) {
        TraceContext traceContext = TraceContext.create(request.getSessionId());

        try {
            log.info("[{}] Processing request from user={}, query length={}",
                    traceContext.getTraceId(), request.getUserId(),
                    request.getQuery().length());

            // Store user message in memory
            if (request.getSessionId() != null) {
                conversationMemory.addUserMessage(request.getSessionId(), request.getQuery());
            }

            // Route request
            GraphState state = agentRouter.route(request, traceContext);

            // Execute appropriate graph
            GraphState result;
            if (state.getRoutingDecision() == GraphState.RoutingDecision.SINGLE) {
                result = executeSingleAgent(state);
            } else {
                result = executeMultiAgent(state);
            }

            // Build response
            long latencyMs = traceContext.getElapsedMs();
            CopilotResponse response = buildResponse(result, latencyMs);

            // Store assistant response in memory
            if (request.getSessionId() != null && result.getFinalResponse() != null) {
                conversationMemory.addAssistantMessage(request.getSessionId(), result.getFinalResponse());
            }

            // Record metrics
            metricsCollector.recordRequest(result, latencyMs);

            log.info("[{}] Request completed: mode={}, steps={}, latency={}ms",
                    traceContext.getTraceId(), response.getModeUsed(),
                    response.getStepsExecuted(), latencyMs);

            return response;

        } catch (Exception e) {
            log.error("[{}] Error processing request: {}", traceContext.getTraceId(), e.getMessage(), e);
            metricsCollector.recordError();

            return CopilotResponse.builder()
                    .traceId(traceContext.getTraceId())
                    .sessionId(request.getSessionId())
                    .answer("An error occurred while processing your request. "
                            + "Please try again or contact support. Trace ID: " + traceContext.getTraceId())
                    .modeUsed(CopilotResponse.AgentModeUsed.SINGLE_AGENT)
                    .stepsExecuted(0)
                    .latencyMs(traceContext.getElapsedMs())
                    .timestamp(Instant.now())
                    .guardrailFlags(Collections.singletonList("ERROR: " + e.getMessage()))
                    .build();
        } finally {
            traceContext.deactivate();
        }
    }

    private GraphState executeSingleAgent(GraphState state) throws Exception {
        log.info("[{}] Executing single-agent workflow", state.getTraceId());

        CompiledGraph<AgentState> compiled = singleAgentGraph.build().compile();
        Map<String, Object> input = Map.of("graphState", state);

        var resultState = compiled.invoke(input);
        return extractResult(resultState);
    }

    private GraphState executeMultiAgent(GraphState state) throws Exception {
        log.info("[{}] Executing multi-agent workflow", state.getTraceId());

        CompiledGraph<AgentState> compiled = multiAgentGraph.build().compile();
        Map<String, Object> input = Map.of("graphState", state);

        var resultState = compiled.invoke(input);
        return extractResult(resultState);
    }

    @SuppressWarnings("unchecked")
    private GraphState extractResult(java.util.Optional<AgentState> resultState) {
        AgentState finalState = resultState.orElseThrow(
                () -> new RuntimeException("Graph execution returned no result"));
        return (GraphState) finalState.value("graphState").orElseThrow(
                () -> new RuntimeException("graphState missing from final agent state"));
    }

    private CopilotResponse buildResponse(GraphState state, long latencyMs) {
        return CopilotResponse.builder()
                .traceId(state.getTraceId())
                .sessionId(state.getSessionId())
                .answer(state.getFinalResponse() != null
                        ? state.getFinalResponse()
                        : "Unable to generate a response. Please rephrase your question.")
                .modeUsed(state.getRoutingDecision() == GraphState.RoutingDecision.SINGLE
                        ? CopilotResponse.AgentModeUsed.SINGLE_AGENT
                        : CopilotResponse.AgentModeUsed.MULTI_AGENT)
                .citations(state.getRetrievedPolicies() != null
                        ? policyQueryAgent.buildCitations(state.getRetrievedPolicies())
                        : Collections.emptyList())
                .triageTicket(state.getTriageTicket())
                .stepsExecuted(state.getCurrentStep())
                .latencyMs(latencyMs)
                .timestamp(Instant.now())
                .guardrailFlags(state.getGuardrailFlags())
                .build();
    }
}
