package com.knowledge.assistant.graph.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Neo4j driver bean for the ka-graph module.
 *
 * <p>Conditional on {@code ka.graph.enabled=true} so the module is inert by default —
 * ka-webapp boots without a running Neo4j (mirrors the {@code ka.rag.reranker.enabled}
 * opt-in pattern). All graph services depend on the {@link Driver} bean, so they are
 * naturally excluded when the flag is off.
 *
 * <p>Connection defaults point at the isolated ka-neo4j container (ports 17474/17687),
 * not the WeKnora-neo4j instance that occupies 7474/7687 — override via environment.
 */
@Configuration
@ConditionalOnProperty(name = "ka.graph.enabled", havingValue = "true")
public class Neo4jConfig {

    @Bean(destroyMethod = "close")
    public Driver neo4jDriver(
            @Value("${ka.graph.neo4j.uri:bolt://localhost:17687}") String uri,
            @Value("${ka.graph.neo4j.user:neo4j}") String user,
            @Value("${ka.graph.neo4j.password:}") String password) {
        return GraphDatabase.driver(uri, AuthTokens.basic(user, password));
    }
}
