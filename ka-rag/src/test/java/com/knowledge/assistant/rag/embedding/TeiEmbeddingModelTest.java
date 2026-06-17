package com.knowledge.assistant.rag.embedding;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeiEmbeddingModelTest {

    private ResponseEntity<List<float[]>> okResp(List<float[]> vs) {
        return new ResponseEntity<>(vs, HttpStatus.OK);
    }

    @Test
    void callParsesBareVectorArray() {
        RestTemplate rest = mock(RestTemplate.class);
        List<float[]> vectors = List.of(
                new float[]{0.1f, 0.2f, 0.3f},
                new float[]{0.4f, 0.5f});
        when(rest.exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class),
                any(ParameterizedTypeReference.class))).thenReturn(okResp(vectors));

        TeiEmbeddingModel model = new TeiEmbeddingModel(rest, "http://localhost:8084");
        EmbeddingResponse out = model.call(new EmbeddingRequest(List.of("a", "b"), null));

        assertThat(out.getResults()).hasSize(2);
        assertThat(out.getResults().get(0).getIndex()).isZero();
        assertThat(out.getResults().get(1).getIndex()).isEqualTo(1);
    }

    @Test
    void callBatchesOverMaxBatchSizeAndPreservesIndex() {
        // TEI CPU forces max_batch_requests=4: 6 inputs must split into 2 batches (4 + 2),
        // and the concatenated result must preserve input order/index.
        RestTemplate rest = mock(RestTemplate.class);
        when(rest.exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(okResp(List.of(
                        new float[]{1f}, new float[]{2f}, new float[]{3f}, new float[]{4f})))
                .thenReturn(okResp(List.of(new float[]{5f}, new float[]{6f})));

        TeiEmbeddingModel model = new TeiEmbeddingModel(rest, "http://x");
        EmbeddingResponse out = model.call(new EmbeddingRequest(List.of("a", "b", "c", "d", "e", "f"), null));

        assertThat(out.getResults()).hasSize(6);
        for (int i = 0; i < 6; i++) {
            assertThat(out.getResults().get(i).getIndex()).isEqualTo(i);
        }
        verify(rest, times(2)).exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
    }
}
