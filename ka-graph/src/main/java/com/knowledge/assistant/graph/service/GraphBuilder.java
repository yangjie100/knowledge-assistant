package com.knowledge.assistant.graph.service;

import com.knowledge.assistant.graph.model.ExtractionResult;
import com.knowledge.assistant.graph.model.ExtractionResult.ExtractedEntity;
import com.knowledge.assistant.graph.model.ExtractionResult.ExtractedRelation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Persists a chunk + its extracted entities/relations into Neo4j.
 *
 * <p>Schema (constraints/indexes created at deploy time):
 * <ul>
 *   <li>(:Chunk {id, docId, text, embedding}) — vector index ka_chunk_embedding (1024d cosine)</li>
 *   <li>(:Entity {name, type, description}), (:Document {docId})</li>
 *   <li>(:Chunk)-[:MENTIONS]->(:Entity), (:Entity)-[:RELATES {type}]->(:Entity)</li>
 * </ul>
 *
 * <p>Uses MERGE (idempotent) so re-importing the same chunk never duplicates nodes. The
 * embedding MUST be a List<Float> — Neo4j's vector index rejects List<Double>.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ka.graph.enabled", havingValue = "true")
public class GraphBuilder {

    private final Driver driver;

    public void buildFromChunk(String chunkId, String docId, String text,
                               List<Float> embedding, ExtractionResult extraction) {
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("""
                        MERGE (d:Document {docId: $docId})
                        MERGE (c:Chunk {id: $chunkId})
                        SET c.docId = $docId, c.text = $text, c.embedding = $embedding
                        MERGE (d)-[:HAS_CHUNK]->(c)
                        """,
                        Map.of("docId", docId, "chunkId", chunkId,
                               "text", text, "embedding", embedding));

                for (ExtractedEntity entity : extraction.entities()) {
                    tx.run("""
                            MERGE (e:Entity {name: $name})
                            SET e.type = $type, e.description = $desc
                            WITH e
                            MATCH (c:Chunk {id: $chunkId})
                            MERGE (c)-[:MENTIONS]->(e)
                            """,
                            Map.of("name", entity.name(), "type", entity.type(),
                                   "desc", entity.description(), "chunkId", chunkId));
                }

                for (ExtractedRelation rel : extraction.relations()) {
                    tx.run("""
                            MATCH (s:Entity {name: $source}), (t:Entity {name: $target})
                            MERGE (s)-[r:RELATES]->(t)
                            SET r.type = $type
                            """,
                            Map.of("source", rel.source(), "target", rel.target(), "type", rel.type()));
                }
                return null;
            });
            log.debug("Built graph for chunk {}: {} entities, {} relations",
                    chunkId, extraction.entities().size(), extraction.relations().size());
        }
    }
}
