package com.copilot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

/**
 * Neo4J configuration — connection properties are in application.yml.
 * Spring Boot auto-configures the driver and Neo4jClient.
 */
@Configuration
@EnableNeo4jRepositories(basePackages = "com.copilot")
public class Neo4jConfig {
    // Auto-configured via spring.neo4j.* properties
}
