package com.knowledge.assistant.rag.embedding;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Activates a TEI-backed EmbeddingModel (bge-m3) when {@code ka.embedding.provider=tei}.
 *
 * <p>Spring AI 1.1.x auto-configures OllamaEmbeddingModel because spring.ai.model.embedding=ollama
 * stays set in application.yml; that bean is harmless when unused. @Primary + @ConditionalOnMissingBean
 * in Spring AI's OllamaEmbeddingAutoConfiguration means our bean (registered first) wins for the
 * EmbeddingModel injection point that RedisVectorStore depends on, so no NoUniqueBeanDefinition.
 * This mirrors how the project already keeps Ollama + ZhiPuAi chat models side-by-side.
 */
@Configuration
@ConditionalOnProperty(name = "ka.embedding.provider", havingValue = "tei")
public class TeiEmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingModel teiEmbeddingModel(
            @Value("${ka.embedding.tei.endpoint}") String endpoint,
            @Value("${ka.embedding.tei.timeout-seconds:60}") int timeoutSeconds) {
        return new TeiEmbeddingModel(endpoint, timeoutSeconds);
    }
}
