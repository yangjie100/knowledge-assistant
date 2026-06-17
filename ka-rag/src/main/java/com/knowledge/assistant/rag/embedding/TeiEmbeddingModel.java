package com.knowledge.assistant.rag.embedding;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Custom EmbeddingModel backed by a HuggingFace Text-Embeddings-Inference (TEI) container
 * running a Chinese-friendly embedder (bge-m3, 1024d). Spring AI 1.1.x has no official TEI
 * embedding starter (issue #2907), so we implement EmbeddingModel directly and POST to TEI's
 * native /embed endpoint which returns a bare JSON array of float vectors: [[...],[...]].
 *
 * <p>The CPU TEI backend forces {@code max_batch_requests=4} (logged at warmup); sending more
 * inputs in one POST makes TEI return 422. {@link #call} therefore batches at most
 * {@link #TEI_MAX_BATCH} inputs per request and concatenates results preserving input order.
 * The two interface abstracts are {@link #call} (batched) and {@link #embed(Document)} (single);
 * convenience overloads (embed(String), dimensions()) come from interface defaults.
 */
@Slf4j
public class TeiEmbeddingModel implements EmbeddingModel {

    // TEI CPU backend forces max_batch_requests=4 at warmup. Batching above this is a 422.
    private static final int TEI_MAX_BATCH = 4;

    private final RestTemplate restTemplate;
    private final String endpoint;

    public TeiEmbeddingModel(String endpoint, int timeoutSeconds) {
        this(buildRestTemplate(timeoutSeconds), endpoint);
    }

    // Test seam: inject a RestTemplate (Mockito mock or MockRestServiceServer-bound).
    TeiEmbeddingModel(RestTemplate restTemplate, String endpoint) {
        this.restTemplate = restTemplate;
        this.endpoint = endpoint;
    }

    private static RestTemplate buildRestTemplate(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int ms = (int) Duration.ofSeconds(timeoutSeconds).toMillis();
        factory.setConnectTimeout(ms);
        factory.setReadTimeout(ms);
        return new RestTemplate(factory);
    }

    @Override
    public float[] embed(Document document) {
        EmbeddingResponse resp = call(new EmbeddingRequest(List.of(document.getText()), null));
        return resp.getResults().get(0).getOutput();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> inputs = request.getInstructions();
        List<float[]> allVectors = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i += TEI_MAX_BATCH) {
            List<String> batch = inputs.subList(i, Math.min(i + TEI_MAX_BATCH, inputs.size()));
            allVectors.addAll(callTei(batch));
        }
        List<Embedding> embeddings = new ArrayList<>(allVectors.size());
        for (int i = 0; i < allVectors.size(); i++) {
            embeddings.add(new Embedding(allVectors.get(i), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    private List<float[]> callTei(List<String> inputs) {
        try {
            ResponseEntity<List<float[]>> resp = restTemplate.exchange(
                    endpoint + "/embed",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("inputs", inputs)),
                    new ParameterizedTypeReference<List<float[]>>() {});
            List<float[]> vectors = resp.getBody();
            if (vectors == null || vectors.isEmpty()) {
                throw new IllegalStateException("TEI /embed returned empty body for batch of " + inputs.size());
            }
            return vectors;
        } catch (RestClientException e) {
            log.error("TEI embedding call failed: {}", e.getMessage());
            throw new RuntimeException("TEI embedding call failed", e);
        }
    }
}
