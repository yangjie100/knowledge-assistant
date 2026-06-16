package com.knowledge.assistant.rag.rerank;

import java.util.Comparator;
import java.util.List;

import com.knowledge.assistant.rag.config.RerankerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

/**
 * Re-ranks RRF-fused recall candidates via TEI. On any failure degrades to rrfScore ordering
 * (topN), so Q&A never breaks. The query is never logged in full (avoid leakage).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RerankService {

    private final RerankClient rerankClient;
    private final RerankerConfig config;

    /**
     * @param candidates RRF-fused candidates (already carry metadata.rrfScore)
     * @param query      user query (not printed in full)
     * @return topN by rerank score, or rrfScore-fallback topN on degradation
     */
    public List<Document> rerank(List<Document> candidates, String query) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<String> texts = candidates.stream().map(Document::getText).toList();
        List<RerankResult> results;
        try {
            results = rerankClient.rerank(query, texts);
        } catch (Exception e) {
            // Top-level safety net: even an unexpected exception must not abort the request.
            log.warn("Rerank threw unexpected error, degrading: {}", e.getMessage());
            results = null;
        }
        if (results == null) {
            log.info("Rerank degraded, falling back to rrfScore ordering ({} candidates)", candidates.size());
            return candidates.stream()
                    .sorted(Comparator.comparingDouble(this::rrfScoreOf).reversed())
                    .limit(config.getTopN())
                    .toList();
        }
        List<Document> reranked = results.stream()
                // Secondary sort guard: TEI declares descending, but defend against version variance.
                .sorted(Comparator.comparingDouble(RerankResult::score).reversed())
                // Bounds guard: a rogue index would otherwise throw IndexOutOfBoundsException and kill the request.
                .filter(r -> r.index() >= 0 && r.index() < candidates.size())
                .limit(config.getTopN())
                .map(r -> {
                    Document d = candidates.get(r.index());
                    d.getMetadata().put("rerankScore", r.score());
                    return d;
                })
                .toList();
        log.info("Rerank applied: {} candidates -> {} results", candidates.size(), reranked.size());
        return reranked;
    }

    private double rrfScoreOf(Document d) {
        return d.getMetadata().get("rrfScore") instanceof Number n ? n.doubleValue() : 0.0;
    }
}
