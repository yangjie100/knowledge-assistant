package com.knowledge.assistant.graph.model;

import java.util.List;

/**
 * Structured output of GLM entity extraction. Populated by Spring AI's
 * BeanOutputConverter (ChatClient...entity(ExtractionResult.class)) which injects the
 * JSON schema into the prompt and parses GLM-5.2's JSON response back into records.
 *
 * <p>Only entities/relations explicitly stated in the source text should appear — the
 * system prompt forbids fabrication, so empty lists are a valid outcome for sparse chunks.
 * Compact constructors null-guard the lists so a model that omits a field never NPEs.
 */
public record ExtractionResult(List<ExtractedEntity> entities, List<ExtractedRelation> relations) {

    public ExtractionResult {
        if (entities == null) entities = List.of();
        if (relations == null) relations = List.of();
    }

    /** A named thing in the domain (component / concept / tool / module / method). */
    public record ExtractedEntity(String name, String type, String description) {}

    /** A typed edge between two entities that co-occur in the chunk. */
    public record ExtractedRelation(String source, String target, String type) {}
}
