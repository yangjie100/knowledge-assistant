package com.knowledge.assistant.rag.rerank;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.knowledge.assistant.rag.config.RerankerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RerankServiceTest {

    @Mock
    private RerankClient rerankClient;

    private RerankerConfig config;

    private RerankService service;

    @BeforeEach
    void setUp() {
        config = new RerankerConfig();
        config.setTopN(2);
        service = new RerankService(rerankClient, config);
    }

    private Document doc(String id, String text, double rrfScore) {
        return new Document(id, text, new HashMap<>(Map.of("rrfScore", rrfScore)));
    }

    @Test
    void reranksByScoreAndWritesRerankScoreAndTruncatesTopN() {
        // TEI scores index2 highest -> should surface first; topN=2 truncates the rest.
        List<Document> candidates = List.of(
                doc("c0", "text0", 0.03),
                doc("c1", "text1", 0.02),
                doc("c2", "text2", 0.01));
        when(rerankClient.rerank(eq("q"), any())).thenReturn(List.of(
                new RerankResult(2, 0.9),
                new RerankResult(0, 0.5),
                new RerankResult(1, 0.1)));

        List<Document> result = service.rerank(candidates, "q");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("c2");
        assertThat(result.get(0).getMetadata()).containsEntry("rerankScore", 0.9);
        assertThat(result.get(1).getId()).isEqualTo("c0");
    }

    @Test
    void degradesToRrfScoreOrderingWhenClientReturnsNull() {
        List<Document> candidates = List.of(
                doc("low", "t0", 0.01),
                doc("high", "t1", 0.05));
        when(rerankClient.rerank(any(), any())).thenReturn(null);

        List<Document> result = service.rerank(candidates, "q");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("high");
        assertThat(result.get(0).getMetadata()).doesNotContainKey("rerankScore");
    }

    @Test
    void emptyCandidatesReturnsEmpty() {
        assertThat(service.rerank(List.of(), "q")).isEmpty();
    }

    @Test
    void filtersOutOfBoundsIndex() {
        List<Document> candidates = List.of(doc("c0", "t0", 0.03));
        when(rerankClient.rerank(any(), any())).thenReturn(List.of(
                new RerankResult(5, 0.99),  // out of bounds -> dropped
                new RerankResult(0, 0.5)));

        List<Document> result = service.rerank(candidates, "q");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("c0");
    }
}
