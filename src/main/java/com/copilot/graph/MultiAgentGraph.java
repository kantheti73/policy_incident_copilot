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
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * LangGraph4J graph for multi-agent incident triage and complex policy workflow.
 *
 * Flow: InputGuardrail → Planner → Researcher → Orchestrator → Critic → OutputGuardrail → END
 *
 * The Critic reviews and may revise the response, but always finalizes it (no retry loop).
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

        StateGraph<AgentState> graph = new StateGraph<>(AgentState::new)

                // Define nodes
                .addNode("input_guardrail", node_async(state -> {
                    GraphState gs = extractGraphState(state);
                    gs = guardrailsEngine.applyInputGuardrails(gs);
                    return Map.of("graphState", (Object) gs);
                }))

                .addNode("planner", node_async(state -> {
                    GraphState gs = extractGraphState(state);
                    if (gs.getError() != null) return Map.of("graphState", (Object) gs);
                    gs = plannerAgent.execute(gs);
                    return Map.of("graphState", (Object) gs);
                }))

                .addNode("researcher", node_async(state -> {
                    GraphState gs = extractGraphState(state);
                    if (gs.getError() != null) return Map.of("graphState", (Object) gs);
                    gs = researcherAgent.execute(gs);
                    return Map.of("graphState", (Object) gs);
                }))

                .addNode("orchestrator", node_async(state -> {
                    GraphState gs = extractGraphState(state);
                    if (gs.getError() != null) return Map.of("graphState", (Object) gs);
                    gs = orchestratorAgent.execute(gs);
                    return Map.of("graphState", (Object) gs);
                }))

                .addNode("critic", node_async(state -> {
                    GraphState gs = extractGraphState(state);
                    if (gs.getError() != null) return Map.of("graphState", (Object) gs);
                    gs = criticAgent.execute(gs);
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
                .addEdge("input_guardrail", "planner")
                .addEdge("planner", "researcher")
                .addEdge("researcher", "orchestrator")
                .addEdge("orchestrator", "critic")
                .addEdge("critic", "output_guardrail")
                .addEdge("output_guardrail", END);

        return graph;
    }

    @SuppressWarnings("unchecked")
    private GraphState extractGraphState(AgentState state) {
        return (GraphState) state.value("graphState").orElseThrow(
                () -> new IllegalStateException("graphState not found in agent state"));
    }
}
