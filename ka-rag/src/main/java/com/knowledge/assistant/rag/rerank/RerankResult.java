package com.knowledge.assistant.rag.rerank;

/**
 * TEI /rerank response item. index = position in the input texts array; score = query-text relevance
 * (TEI returns the array already sorted by score descending).
 */
public record RerankResult(int index, double score) {
}
