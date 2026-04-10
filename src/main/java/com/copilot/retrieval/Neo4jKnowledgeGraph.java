package com.copilot.retrieval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Neo4J-backed knowledge graph for policy relationships, dependencies,
 * and incident pattern lookups.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Neo4jKnowledgeGraph {

    private final Neo4jClient neo4jClient;

    /**
     * Find policies related to the query topic via graph traversal.
     */
    public List<String> findRelatedPolicies(String topic) {
        log.debug("Querying Neo4J for policies related to: {}", topic);

        Collection<String> results = neo4jClient.query("""
                WITH [w IN split(toLower($topic), ' ') WHERE size(w) > 3] AS words
                MATCH (p:Policy)-[:RELATES_TO|DEPENDS_ON|SUPERSEDES|REFERENCES*1..2]-(related:Policy)
                WHERE ANY(word IN words WHERE
                        toLower(p.title) CONTAINS word
                     OR toLower(p.category) CONTAINS word)
                RETURN DISTINCT related.title + ' (v' + related.version + ')' AS relatedPolicy
                LIMIT 10
                """)
                .bind(topic).to("topic")
                .fetchAs(String.class)
                .mappedBy((typeSystem, record) -> record.get("relatedPolicy").asString())
                .all();

        log.info("Found {} related policies in knowledge graph", results.size());
        return List.copyOf(results);
    }

    /**
     * Find known incident patterns matching the described symptoms.
     */
    public List<String> findIncidentPatterns(String symptoms) {
        Collection<String> results = neo4jClient.query("""
                MATCH (i:IncidentPattern)-[:HAS_SYMPTOM]->(s:Symptom)
                WHERE toLower(s.description) CONTAINS toLower($symptoms)
                RETURN i.name + ': ' + i.resolution AS pattern
                LIMIT 5
                """)
                .bind(symptoms).to("symptoms")
                .fetchAs(String.class)
                .mappedBy((typeSystem, record) -> record.get("pattern").asString())
                .all();

        return List.copyOf(results);
    }

    /**
     * Find the escalation path for a given policy category.
     */
    public List<String> findEscalationPath(String category) {
        Collection<String> results = neo4jClient.query("""
                MATCH path = (c:Category {name: $category})-[:ESCALATES_TO*]->(team:Team)
                RETURN team.name + ' (' + team.level + ')' AS escalation
                ORDER BY length(path)
                """)
                .bind(category).to("category")
                .fetchAs(String.class)
                .mappedBy((typeSystem, record) -> record.get("escalation").asString())
                .all();

        return List.copyOf(results);
    }
}
