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
import java.util.Optional;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
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

        StateGraph<AgentState> graph = new StateGraph<>(AgentState::new);

        // Define nodes
        graph.addNode("input_guardrail", node_async(state -> {
            GraphState gs = extractGraphState(state);
            gs = guardrailsEngine.applyInputGuardrails(gs);
            return updateState(state, gs);
        }));

        graph.addNode("policy_query", node_async(state -> {
            GraphState gs = extractGraphState(state);
            if (gs.getError() != null) return state.data();
            gs = policyQueryAgent.execute(gs);
            return updateState(state, gs);
        }));

        graph.addNode("output_guardrail", node_async(state -> {
            GraphState gs = extractGraphState(state);
            if (gs.getError() != null) return state.data();
            gs = guardrailsEngine.applyOutputGuardrails(gs);
            return updateState(state, gs);
        }));

        // Define edges
        graph.addEdge("input_guardrail", "policy_query");
        graph.addEdge("policy_query", "output_guardrail");
        graph.addEdge("output_guardrail", END);

        // Set entry point
        graph.setEntryPoint("input_guardrail");

        return graph;
    }

    private GraphState extractGraphState(AgentState state) {
        return (GraphState) state.data().get("graphState");
    }

    private Map<String, Object> updateState(AgentState state, GraphState gs) {
        var data = new java.util.HashMap<>(state.data());
        data.put("graphState", gs);
        return data;
    }
}
