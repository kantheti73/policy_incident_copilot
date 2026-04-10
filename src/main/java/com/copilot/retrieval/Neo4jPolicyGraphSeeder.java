package com.copilot.retrieval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Seeds the Neo4j knowledge graph with Policy nodes and their relationships
 * on application startup. Idempotent — safe to re-run.
 *
 * Schema:
 *   (:Policy {documentId, title, version, category})
 *   (:Policy)-[:RELATES_TO]->(:Policy)
 *   (:Policy)-[:DEPENDS_ON]->(:Policy)
 *   (:Policy)-[:REFERENCES]->(:Policy)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Neo4jPolicyGraphSeeder implements ApplicationRunner {

    private final Neo4jClient neo4jClient;

    private static final List<Map<String, Object>> POLICIES = List.of(
            Map.of("documentId", "SEC-001", "title", "Enterprise Password Policy", "version", "3.2", "category", "Access Control"),
            Map.of("documentId", "SEC-002", "title", "Multi-Factor Authentication Policy", "version", "2.1", "category", "Access Control"),
            Map.of("documentId", "SEC-003", "title", "Network Security and Firewall Policy", "version", "4.0", "category", "Network Security"),
            Map.of("documentId", "SEC-004", "title", "Endpoint Security and Device Hardening Policy", "version", "2.5", "category", "Endpoint Security"),
            Map.of("documentId", "SEC-005", "title", "Data Protection and Classification Policy", "version", "3.0", "category", "Data Protection"),
            Map.of("documentId", "SEC-006", "title", "Incident Response Policy", "version", "2.8", "category", "Incident Response"),
            Map.of("documentId", "SEC-007", "title", "Access Control and Identity Management Policy", "version", "3.1", "category", "Access Control"),
            Map.of("documentId", "SEC-008", "title", "Secure Software Development and DevSecOps Policy", "version", "2.0", "category", "Application Security"),
            Map.of("documentId", "SEC-009", "title", "Email and Phishing Protection Policy", "version", "1.8", "category", "Security Awareness"),
            Map.of("documentId", "SEC-010", "title", "Cloud Security and SaaS Governance Policy", "version", "2.2", "category", "Cloud Security"),
            Map.of("documentId", "OPS-001", "title", "IT Change Management Policy", "version", "2.3", "category", "IT Operations"),
            Map.of("documentId", "OPS-002", "title", "Backup and Disaster Recovery Policy", "version", "2.5", "category", "IT Operations")
    );

    // Relationships: [fromId, type, toId]
    private static final List<String[]> RELATIONSHIPS = List.of(
            // Authentication cluster: passwords ↔ MFA ↔ access control
            new String[]{"SEC-001", "RELATES_TO", "SEC-002"},
            new String[]{"SEC-001", "RELATES_TO", "SEC-007"},
            new String[]{"SEC-002", "RELATES_TO", "SEC-007"},
            new String[]{"SEC-002", "REFERENCES", "SEC-001"},

            // Network ↔ Endpoint ↔ Cloud (perimeter and device security)
            new String[]{"SEC-003", "RELATES_TO", "SEC-004"},
            new String[]{"SEC-003", "RELATES_TO", "SEC-010"},
            new String[]{"SEC-004", "DEPENDS_ON", "OPS-001"},
            new String[]{"SEC-010", "RELATES_TO", "SEC-005"},

            // Data protection touches multiple domains
            new String[]{"SEC-005", "RELATES_TO", "SEC-008"},
            new String[]{"SEC-005", "RELATES_TO", "SEC-010"},
            new String[]{"SEC-005", "REFERENCES", "SEC-007"},

            // Incident response is the hub for security events
            new String[]{"SEC-006", "RELATES_TO", "SEC-009"},
            new String[]{"SEC-006", "REFERENCES", "OPS-002"},
            new String[]{"SEC-006", "REFERENCES", "OPS-001"},
            new String[]{"SEC-009", "RELATES_TO", "SEC-006"},

            // Secure development depends on data classification and access control
            new String[]{"SEC-008", "DEPENDS_ON", "SEC-005"},
            new String[]{"SEC-008", "DEPENDS_ON", "SEC-007"},
            new String[]{"SEC-008", "REFERENCES", "OPS-001"},

            // Operations: change management gates most security changes
            new String[]{"OPS-001", "RELATES_TO", "SEC-003"},
            new String[]{"OPS-001", "RELATES_TO", "SEC-004"},
            new String[]{"OPS-002", "RELATES_TO", "SEC-006"}
    );

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("Seeding Neo4j policy graph: {} policies, {} relationships",
                    POLICIES.size(), RELATIONSHIPS.size());

            for (Map<String, Object> policy : POLICIES) {
                neo4jClient.query("""
                        MERGE (p:Policy {documentId: $documentId})
                        SET p.title = $title,
                            p.version = $version,
                            p.category = $category
                        """)
                        .bindAll(policy)
                        .run();
            }

            for (String[] rel : RELATIONSHIPS) {
                String cypher = String.format("""
                        MATCH (a:Policy {documentId: $from})
                        MATCH (b:Policy {documentId: $to})
                        MERGE (a)-[:%s]->(b)
                        """, rel[1]);

                neo4jClient.query(cypher)
                        .bind(rel[0]).to("from")
                        .bind(rel[2]).to("to")
                        .run();
            }

            Long policyCount = neo4jClient.query("MATCH (p:Policy) RETURN count(p) AS c")
                    .fetchAs(Long.class)
                    .mappedBy((ts, r) -> r.get("c").asLong())
                    .one()
                    .orElse(0L);

            Long relCount = neo4jClient.query("MATCH (:Policy)-[r]->(:Policy) RETURN count(r) AS c")
                    .fetchAs(Long.class)
                    .mappedBy((ts, r) -> r.get("c").asLong())
                    .one()
                    .orElse(0L);

            log.info("Neo4j policy graph ready: {} Policy nodes, {} relationships", policyCount, relCount);
        } catch (Exception e) {
            log.error("Failed to seed Neo4j policy graph: {}", e.getMessage(), e);
        }
    }
}
