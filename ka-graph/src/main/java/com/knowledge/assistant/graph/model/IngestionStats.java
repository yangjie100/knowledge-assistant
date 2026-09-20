package com.knowledge.assistant.graph.model;

/**
 * Summary of a one-shot graph ingestion run: how many chunks were persisted and how many
 * entities/relations were extracted from them. Returned by the /api/graph/ingest endpoint.
 */
public record IngestionStats(int chunks, int entities, int relations) {
}
