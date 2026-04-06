package com.copilot.graph;

import com.copilot.agent.multi.CriticAgent;
import com.copilot.agent.multi.OrchestratorAgent;
import com.copilot.agent.multi.PlannerAgent;
import com.copilot.agent.multi.ResearcherAgent;
import com.copilot.guardrails.GuardrailsEngine;
import com.copilot.model.GraphState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * LangGraph4J graph for multi-agent incident triage and complex policy workflow.
 *
 * Flow: InputGuardrail → Planner → Researcher → Orchestrator → Critic → OutputGuardrail → END
 *
 * The Critic can loop back to Researcher if the response needs revision (max 1 retry).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultiAgentGraph {

    private final PlannerAgent plannerAgent;
    private final ResearcherAgent researcherAgent;
    private final OrchestratorAgent orchestratorAgent;
    private final CriticAgent criticAgent;
    private final GuardrailsEngine guardrailsEngine;

    public StateGraph<AgentState> build() throws Exception {
        log.info("Building Multi-Agent LangGraph4J graph");

        StateGraph<AgentState> graph = new StateGraph<>(AgentState::new);

        // Define nodes
        graph.addNode("input_guardrail", node_async(state -> {
            GraphState gs = extractGraphState(state);
            gs = guardrailsEngine.applyInputGuardrails(gs);
            return updateState(state, gs);
        }));

        graph.addNode("planner", node_async(state -> {
            GraphState gs = extractGraphState(state);
            if (gs.getError() != null) return state.data();
            gs = plannerAgent.execute(gs);
            return updateState(state, gs);
        }));

        graph.addNode("researcher", node_async(state -> {
            GraphState gs = extractGraphState(state);
            if (gs.getError() != null) return state.data();
            gs = researcherAgent.execute(gs);
            return updateState(state, gs);
        }));

        graph.addNode("orchestrator", node_async(state -> {
            GraphState gs = extractGraphState(state);
            if (gs.getError() != null) return state.data();
            gs = orchestratorAgent.execute(gs);
            return updateState(state, gs);
        }));

        graph.addNode("critic", node_async(state -> {
            GraphState gs = extractGraphState(state);
            if (gs.getError() != null) return state.data();
            gs = criticAgent.execute(gs);
            return updateState(state, gs);
        }));

        graph.addNode("output_guardrail", node_async(state -> {
            GraphState gs = extractGraphState(state);
            if (gs.getError() != null) return state.data();
            gs = guardrailsEngine.applyOutputGuardrails(gs);
            return updateState(state, gs);
        }));

        // Define edges
        graph.addEdge("input_guardrail", "planner");
        graph.addEdge("planner", "researcher");
        graph.addEdge("researcher", "orchestrator");
        graph.addEdge("orchestrator", "critic");

        // Conditional edge: Critic can approve → output_guardrail or revise → researcher
        graph.addConditionalEdges("critic",
                edge_async(state -> {
                    GraphState gs = extractGraphState(state);
                    if (gs.isSafetyApproved() || gs.isStepLimitReached()) {
                        return "output_guardrail";
                    }
                    return "researcher";
                }),
                Map.of(
                        "output_guardrail", "output_guardrail",
                        "researcher", "researcher"
                )
        );

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
