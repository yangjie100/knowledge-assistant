package com.knowledge.assistant.rag.rerank;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import com.knowledge.assistant.rag.config.RerankerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Calls HuggingFace TEI /rerank endpoint for cross-encoder re-ranking.
 * RestTemplate ships with MappingJackson2HttpMessageConverter, so record (de)serialization is automatic.
 * Any failure (connection/timeout/incomplete response) returns null to signal RerankService to degrade.
 */
@Slf4j
@Component
public class RerankClient {

    private final RestTemplate restTemplate;
    private final String endpoint;

    public RerankClient(RerankerConfig config) {
        this(buildRestTemplate(config), config.getEndpoint());
    }

    // Test seam: inject a RestTemplate directly (Mockito mock or MockRestServiceServer-bound).
    RerankClient(RestTemplate restTemplate, String endpoint) {
        this.restTemplate = restTemplate;
        this.endpoint = endpoint;
    }

    private static RestTemplate buildRestTemplate(RerankerConfig config) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMs = (int) Duration.ofSeconds(config.getTimeoutSeconds()).toMillis();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return new RestTemplate(factory);
    }

    /**
     * @return ranked results, or null to signal degradation (connection/timeout/incomplete body).
     */
    public List<RerankResult> rerank(String query, List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        try {
            ResponseEntity<RerankResult[]> resp = restTemplate.postForEntity(
                    endpoint + "/rerank",
                    new RerankRequest(query, texts, true),
                    RerankResult[].class);
            RerankResult[] body = resp.getBody();
            if (body == null) {
                log.warn("Rerank response body null, degrading");
                return null;
            }
            // Length check: a truncated/garbled response would make indices dangle.
            if (body.length != texts.size()) {
                log.warn("Rerank response length mismatch: got {} expected {}, degrading",
                        body.length, texts.size());
                return null;
            }
            return Arrays.asList(body);
        } catch (ResourceAccessException e) {
            log.warn("Rerank call failed (connect/timeout), degrading: {}", e.getMessage());
            return null;
        } catch (RestClientException e) {
            log.warn("Rerank call failed, degrading: {}", e.getMessage());
            return null;
        }
    }
}
