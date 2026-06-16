package com.knowledge.assistant.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Reranker (TEI bge-reranker-v2-m3) configuration.
 * Mirrors ChunkConfig style: @Data + @Component + @ConfigurationProperties.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ka.rag.reranker")
public class RerankerConfig {

    // Inert-until-configured: TEI is extra infra, must NOT assume always deployed.
    // Default off avoids degraded-every-request + WARN spam when TEI is absent.
    private boolean enabled = false;

    // TEI /rerank endpoint. host dev -> localhost:8082; compose -> http://tei:80 via TEI_ENDPOINT env.
    private String endpoint = "http://localhost:8082";

    // Recall budget when enabled: pull this many candidates for the reranker to re-rank.
    private int recallTopK = 20;

    // Recall similarity threshold when enabled. 0.7 underfills 20 on Chinese corpora, hence 0.5.
    private double recallThreshold = 0.5;

    // Final top-N kept after rerank.
    private int topN = 5;

    // TEI call timeout in seconds.
    private int timeoutSeconds = 10;
}
