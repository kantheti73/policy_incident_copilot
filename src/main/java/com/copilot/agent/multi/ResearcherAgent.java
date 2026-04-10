package com.copilot.agent.multi;

import com.copilot.model.GraphState;
import com.copilot.model.PolicySnippet;
import com.copilot.retrieval.PolicyRetriever;
import com.copilot.retrieval.Neo4jKnowledgeGraph;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Researcher agent: executes plan steps by retrieving and analyzing information
 * from policy documents, knowledge graph, and logs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResearcherAgent {

    private final ChatLanguageModel chatModel;
    private final PolicyRetriever policyRetriever;
    private final Neo4jKnowledgeGraph knowledgeGraph;

    private static final String SYSTEM_PROMPT = """
            You are a Researcher agent in a multi-agent incident triage and policy system.

            Your job is to execute research tasks from a plan. For each task:
            - Use the provided policy context to find relevant information.
            - Use the knowledge graph relationships to understand policy dependencies.
            - If logs are provided, identify error patterns, timestamps, and affected systems.
            - Provide factual findings with specific references.
            - Flag any contradictions or gaps in available information.

            Be thorough but concise. Cite sources for every finding.
            """;

    public GraphState execute(GraphState state) {
        log.info("[{}] ResearcherAgent executing plan steps in parallel", state.getTraceId());
        state.incrementStep();

        // Retrieve policy documents relevant to the overall query
        List<PolicySnippet> snippets = policyRetriever.retrieve(state.getSanitizedQuery(), 10);
        state.setRetrievedPolicies(snippets);

        // Query knowledge graph for policy relationships
        List<String> relatedPolicies = knowledgeGraph.findRelatedPolicies(state.getSanitizedQuery());

        // Build research context
        String policyContext = snippets.stream()
                .map(s -> String.format("[%s v%s] %s", s.getTitle(), s.getVersion(), s.getContent()))
                .collect(Collectors.joining("\n\n"));

        String graphContext = relatedPolicies.isEmpty()
                ? "No additional policy relationships found."
                : "Related policies from knowledge graph:\n" + String.join("\n", relatedPolicies);

        String logsContext = state.getRawLogs() != null ? "Logs:\n" + state.getRawLogs() : "";

        // Execute plan steps in parallel — each step is an independent LLM call
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, state.getPlan().size()));
        try {
            List<CompletableFuture<Map.Entry<String, String>>> futures = state.getPlan().stream()
                    .map(planStep -> CompletableFuture.supplyAsync(
                            () -> Map.entry(planStep, executePlanStep(planStep, policyContext, graphContext, logsContext)),
                            executor))
                    .toList();

            Map<String, String> findings = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (a, b) -> a,
                            java.util.LinkedHashMap::new));

            state.setResearchFindings(findings);
            log.info("[{}] ResearcherAgent completed {} findings (parallel)", state.getTraceId(), findings.size());
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }

        return state;
    }

    private String executePlanStep(String planStep, String policyContext, String graphContext, String logsContext) {
        String researchPrompt = String.format("""
                Plan step to execute: %s

                Policy context:
                %s

                %s

                %s

                Provide your findings for this step.
                """,
                planStep, policyContext, graphContext, logsContext);

        AiMessage response = chatModel.generate(
                SystemMessage.from(SYSTEM_PROMPT),
                UserMessage.from(researchPrompt)
        ).content();

        return response.text();
    }
}
