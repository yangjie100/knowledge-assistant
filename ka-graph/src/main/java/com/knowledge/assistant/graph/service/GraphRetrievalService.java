package com.knowledge.assistant.graph.service;

import com.knowledge.assistant.graph.model.GraphRetrievalResult;
import com.knowledge.assistant.graph.model.GraphRetrievalResult.ChunkHit;
import com.knowledge.assistant.graph.model.GraphRetrievalResult.EntitySubgraph;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 3-step GraphRAG retrieval (the "HybridRAG" pattern from arXiv:2507.03608):
 * <ol>
 *   <li><b>Vector recall</b> — embed the query, KNN over the Neo4j vector index
 *       (db.index.vector.queryNodes) for top-K Chunk nodes.</li>
 *   <li><b>Graph expand</b> — from each recalled chunk, traverse MENTIONS to entities,
 *       then RELATES*1..hops to pull the local subgraph (the multi-hop signal a pure
 *       vector store cannot surface).</li>
 *   <li><b>Assemble</b> — pack chunks + subgraph into a context string for the LLM.</li>
 * </ol>
 *
 * <p>Reuses ka-rag's EmbeddingModel (bge-m3 1024d via TEI, matching the index dimension).
 * Any failure degrades to an empty result so callers can fall back to HybridRetrieval.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ka.graph.enabled", havingValue = "true")
public class GraphRetrievalService {

    private final Driver driver;
    private final EmbeddingModel embeddingModel;

    @Value("${ka.graph.retrieval.top-k:3}")
    private int topK;

    @Value("${ka.graph.retrieval.hops:2}")
    private int hops;

    public GraphRetrievalResult retrieve(String query) {
        log.info("Graph retrieval for query: {}", query);
        try {
            List<Float> queryEmb = toFloatList(embeddingModel.embed(query));
            List<ChunkHit> chunks = vectorSearch(queryEmb);
            if (chunks.isEmpty()) {
                log.info("Graph vector search returned no chunks for query");
                return GraphRetrievalResult.empty(query);
            }
            List<EntitySubgraph> subgraph = expandEntities(chunks);
            log.info("Graph retrieval done: {} chunks, {} entities", chunks.size(), subgraph.size());
            return new GraphRetrievalResult(query, chunks, subgraph);
        } catch (Exception e) {
            log.warn("Graph retrieval failed (returning empty): {}", e.getMessage());
            return GraphRetrievalResult.empty(query);
        }
    }

    private List<ChunkHit> vectorSearch(List<Float> emb) {
        List<ChunkHit> chunks = new ArrayList<>();
        try (Session session = driver.session()) {
            Result result = session.run("""
                    CALL db.index.vector.queryNodes($indexName, $k, $emb)
                    YIELD node, score
                    RETURN node.id AS id, node.docId AS docId, node.text AS text, score
                    """,
                    Map.of("indexName", "ka_chunk_embedding", "k", topK, "emb", emb));
            while (result.hasNext()) {
                Record r = result.next();
                chunks.add(new ChunkHit(
                        r.get("id").asString(),
                        r.get("docId").isNull() ? "" : r.get("docId").asString(),
                        r.get("text").asString(),
                        r.get("score").asDouble()));
            }
        }
        return chunks;
    }

    private List<EntitySubgraph> expandEntities(List<ChunkHit> chunks) {
        List<String> chunkIds = chunks.stream().map(ChunkHit::id).toList();
        List<EntitySubgraph> subgraph = new ArrayList<>();
        // hops is an @Value int, so interpolating into the depth bound is safe (no Cypher
        // injection surface). Parameterising variable-length depth is not supported.
        String cypher = """
                MATCH (c:Chunk)-[:MENTIONS]->(e:Entity)
                WHERE c.id IN $chunkIds
                OPTIONAL MATCH (e)-[:RELATES*1..%d]-(neighbor:Entity)
                RETURN e.name AS entity, e.type AS type, e.description AS desc,
                       collect(DISTINCT neighbor.name) AS neighbors
                """.formatted(hops);
        try (Session session = driver.session()) {
            Result result = session.run(cypher, Map.of("chunkIds", chunkIds));
            while (result.hasNext()) {
                Record r = result.next();
                List<String> neighbors = r.get("neighbors").isNull()
                        ? List.of()
                        : r.get("neighbors").asList(v -> v.asString());
                subgraph.add(new EntitySubgraph(
                        r.get("entity").asString(),
                        r.get("type").isNull() ? "" : r.get("type").asString(),
                        r.get("desc").isNull() ? "" : r.get("desc").asString(),
                        neighbors));
            }
        }
        return subgraph;
    }

    private static List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) list.add(f);
        return list;
    }
}
