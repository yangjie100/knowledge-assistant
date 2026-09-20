package com.knowledge.assistant.graph.model;

import java.util.List;

/**
 * Outcome of the 3-step GraphRAG retrieval: vector-recalled chunks plus the entity
 * subgraph expanded around them. {@link #toContext()} renders both into a single text
 * block that downstream LLM steps consume as augmented context.
 */
public record GraphRetrievalResult(String query, List<ChunkHit> chunks, List<EntitySubgraph> subgraph) {

    public GraphRetrievalResult {
        if (chunks == null) chunks = List.of();
        if (subgraph == null) subgraph = List.of();
    }

    public static GraphRetrievalResult empty(String query) {
        return new GraphRetrievalResult(query, List.of(), List.of());
    }

    /** A chunk node matched by the vector index, with its cosine score. */
    public record ChunkHit(String id, String docId, String text, double score) {}

    /** An entity mentioned by a recalled chunk, plus its 1..N-hop RELATES neighbours. */
    public record EntitySubgraph(String entity, String type, String description, List<String> neighbors) {
        public EntitySubgraph {
            if (neighbors == null) neighbors = List.of();
        }
    }

    public String toContext() {
        StringBuilder sb = new StringBuilder();
        if (!chunks.isEmpty()) {
            sb.append("【相关文档片段】\n");
            for (int i = 0; i < chunks.size(); i++) {
                sb.append(i + 1).append(". ").append(chunks.get(i).text()).append("\n");
            }
        }
        if (!subgraph.isEmpty()) {
            sb.append("\n【相关实体与关系】\n");
            for (EntitySubgraph e : subgraph) {
                sb.append("- ").append(e.entity());
                if (!e.type().isBlank()) sb.append("（").append(e.type()).append("）");
                if (!e.description().isBlank()) sb.append("：").append(e.description());
                if (!e.neighbors().isEmpty()) {
                    sb.append("  关联实体：").append(String.join("、", e.neighbors()));
                }
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }
}
