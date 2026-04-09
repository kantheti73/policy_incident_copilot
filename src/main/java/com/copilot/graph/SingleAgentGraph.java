package com.copilot.graph;

import com.copilot.agent.single.PolicyQueryAgent;
import com.copilot.guardrails.GuardrailsEngine;
import com.copilot.model.GraphState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * LangGraph4J graph for single-agent policy Q&A workflow.
 *
 * Flow: InputGuardrail → PolicyQuery → OutputGuardrail → END
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SingleAgentGraph {

    private final PolicyQueryAgent policyQueryAgent;
    private final GuardrailsEngine guardrailsEngine;

    public StateGraph<AgentState> build() throws Exception {
        log.info("Building Single-Agent LangGraph4J graph");

        StateGraph<AgentState> graph = new StateGraph<>(AgentState::new)

                // Define nodes
                .addNode("input_guardrail", node_async(state -> {
                    GraphState gs = extractGraphState(state);
                    gs = guardrailsEngine.applyInputGuardrails(gs);
                    return Map.of("graphState", (Object) gs);
                }))

                .addNode("policy_query", node_async(state -> {
                    GraphState gs = extractGraphState(state);
                    if (gs.getError() != null) return Map.of("graphState", (Object) gs);
                    gs = policyQueryAgent.execute(gs);
                    return Map.of("graphState", (Object) gs);
                }))

                .addNode("output_guardrail", node_async(state -> {
                    GraphState gs = extractGraphState(state);
                    if (gs.getError() != null) return Map.of("graphState", (Object) gs);
                    gs = guardrailsEngine.applyOutputGuardrails(gs);
                    return Map.of("graphState", (Object) gs);
                }))

                // Define edges
                .addEdge(START, "input_guardrail")
                .addEdge("input_guardrail", "policy_query")
                .addEdge("policy_query", "output_guardrail")
                .addEdge("output_guardrail", END);

        return graph;
    }

    @SuppressWarnings("unchecked")
    private GraphState extractGraphState(AgentState state) {
        return (GraphState) state.value("graphState").orElseThrow(
                () -> new IllegalStateException("graphState not found in agent state"));
    }
}
