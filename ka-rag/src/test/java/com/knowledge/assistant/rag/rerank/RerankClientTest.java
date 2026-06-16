package com.knowledge.assistant.rag.rerank;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RerankClientTest {

    // Use a concrete String url matcher: a bare any() resolves ambiguously between the
    // postForEntity(String,...) and postForEntity(URI,...) overloads, registering the stub on
    // the wrong signature and tripping strict stubbing. A concrete String forces the String overload.
    private static final String RERANK_URL = "http://tei/rerank";

    @Mock
    private RestTemplate restTemplate;

    private RerankClient client;

    @BeforeEach
    void setUp() {
        client = new RerankClient(restTemplate, "http://tei");
    }

    @Test
    void parsesResponseArray() {
        RerankResult[] body = {new RerankResult(0, 0.9), new RerankResult(1, 0.3)};
        when(restTemplate.postForEntity(eq(RERANK_URL), any(), eq(RerankResult[].class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        List<RerankResult> result = client.rerank("q", List.of("a", "b"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).score()).isEqualTo(0.9);
    }

    @Test
    void returnsNullOnLengthMismatch() {
        RerankResult[] body = {new RerankResult(0, 0.9)};
        when(restTemplate.postForEntity(eq(RERANK_URL), any(), eq(RerankResult[].class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        assertThat(client.rerank("q", List.of("a", "b"))).isNull();
    }

    @Test
    void returnsNullOnNullBody() {
        when(restTemplate.postForEntity(eq(RERANK_URL), any(), eq(RerankResult[].class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        assertThat(client.rerank("q", List.of("a"))).isNull();
    }

    @Test
    void returnsNullOnTimeout() {
        when(restTemplate.postForEntity(eq(RERANK_URL), any(), eq(RerankResult[].class)))
                .thenThrow(new ResourceAccessException("timeout"));

        assertThat(client.rerank("q", List.of("a"))).isNull();
    }

    @Test
    void emptyTextsReturnsEmptyWithoutCallingEndpoint() {
        assertThat(client.rerank("q", List.of())).isEmpty();
    }
}
