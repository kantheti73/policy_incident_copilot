package com.copilot.agent.single;

import com.copilot.model.CopilotResponse;
import com.copilot.model.GraphState;
import com.copilot.model.PolicySnippet;
import com.copilot.retrieval.PolicyRetriever;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Single-agent for straightforward policy Q&A.
 * Retrieves relevant policy snippets and generates a cited response.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyQueryAgent {

    private final ChatLanguageModel chatModel;
    private final PolicyRetriever policyRetriever;

    private static final String SYSTEM_PROMPT = """
            You are a Policy Copilot assistant. Your role is to answer employee questions
            about company policies and procedures accurately and concisely.

            Rules:
            - Always cite the specific policy document, version, and effective date.
            - If the retrieved context does not contain the answer, say so clearly.
            - Never fabricate policy information.
            - Do not provide advice that contradicts established policies.
            - Keep responses concise and actionable.

            Format citations as: [PolicyTitle vVersion (EffectiveDate)]
            """;

    public GraphState execute(GraphState state) {
        log.info("[{}] PolicyQueryAgent executing for query: {}",
                state.getTraceId(), state.getSanitizedQuery());
        state.incrementStep();

        // Retrieve relevant policy documents
        List<PolicySnippet> snippets = policyRetriever.retrieve(
                state.getSanitizedQuery(), 5);
        state.setRetrievedPolicies(snippets);

        // Build context from retrieved snippets
        String context = snippets.stream()
                .map(s -> String.format("--- %s (v%s, effective %s) ---\n%s",
                        s.getTitle(), s.getVersion(), s.getEffectiveDate(), s.getContent()))
                .collect(Collectors.joining("\n\n"));

        String userPrompt = String.format("""
                Context from policy documents:
                %s

                User question: %s

                Provide a clear, cited answer based on the context above.
                """, context, state.getSanitizedQuery());

        // Call LLM
        AiMessage response = chatModel.generate(
                SystemMessage.from(SYSTEM_PROMPT),
                UserMessage.from(userPrompt)
        ).content();

        state.setFinalResponse(response.text());
        state.setSafetyApproved(true);

        log.info("[{}] PolicyQueryAgent completed in {} steps",
                state.getTraceId(), state.getCurrentStep());
        return state;
    }

    public List<CopilotResponse.Citation> buildCitations(List<PolicySnippet> snippets) {
        return snippets.stream()
                .map(s -> CopilotResponse.Citation.builder()
                        .documentId(s.getDocumentId())
                        .documentTitle(s.getTitle())
                        .snippet(s.getContent().substring(0, Math.min(200, s.getContent().length())))
                        .version(s.getVersion())
                        .effectiveDate(s.getEffectiveDate())
                        .relevanceScore(s.getScore())
                        .build())
                .collect(Collectors.toList());
    }
}
