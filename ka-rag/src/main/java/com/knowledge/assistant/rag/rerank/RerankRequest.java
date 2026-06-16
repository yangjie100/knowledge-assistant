package com.knowledge.assistant.rag.rerank;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TEI /rerank request body. Field names must match TEI expected JSON.
 * rawScores is mapped to snake_case "raw_scores" via @JsonProperty; otherwise TEI returns
 * normalized probability instead of raw logit score.
 */
public record RerankRequest(String query, List<String> texts,
                            @JsonProperty("raw_scores") boolean rawScores) {
}
