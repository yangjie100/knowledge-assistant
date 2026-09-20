package com.knowledge.assistant.graph.service;

import com.knowledge.assistant.graph.model.ExtractionResult;
import com.knowledge.assistant.graph.model.IngestionStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * One-shot backfill: walks every chunk already ingested into the Redis vector store
 * (keyed chunk:text:{chunkId} -> Hash{content, docId}, written by EmbeddingService) and
 * persists it into the Neo4j graph — extract entities/relations (GLM), re-embed (bge-m3
 * 1024d to match the index dim), then GraphBuilder MERGEs Chunk/Entity/Document nodes plus
 * MENTIONS/RELATES edges.
 *
 * <p>Reuses the SAME chunkId as the Redis vector store, so vector-recalled Chunk.id values
 * join cleanly against MENTIONS edges during retrieval. MERGE makes re-import idempotent.
 * Conditional on ka.graph.enabled; triggered via POST /api/graph/ingest (manual, not on boot,
 * since each chunk costs one LLM extraction call).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ka.graph.enabled", havingValue = "true")
public class GraphIngestionService {

    private static final String CHUNK_TEXT_PREFIX = "chunk:text:";

    private final StringRedisTemplate redisTemplate;
    private final EntityExtractor entityExtractor;
    private final GraphBuilder graphBuilder;
    private final EmbeddingModel embeddingModel;

    public IngestionStats ingestAll() {
        Set<String> keys = redisTemplate.keys(CHUNK_TEXT_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            log.warn("No chunks found in Redis (chunk:text:*). Upload docs via /api/knowledge/upload first.");
            return new IngestionStats(0, 0, 0);
        }
        log.info("Graph ingestion starting for {} chunks", keys.size());

        int chunks = 0, entities = 0, relations = 0, failed = 0;
        for (String key : keys) {
            String chunkId = key.substring(CHUNK_TEXT_PREFIX.length());
            Object contentObj = redisTemplate.opsForHash().get(key, "content");
            Object docIdObj = redisTemplate.opsForHash().get(key, "docId");
            if (contentObj == null) {
                continue;
            }
            String text = contentObj.toString();
            String docId = docIdObj == null ? "unknown" : docIdObj.toString();
            try {
                ExtractionResult extraction = entityExtractor.extract(text);
                List<Float> embedding = toFloatList(embeddingModel.embed(text));
                graphBuilder.buildFromChunk(chunkId, docId, text, embedding, extraction);
                chunks++;
                entities += extraction.entities().size();
                relations += extraction.relations().size();
            } catch (Exception e) {
                failed++;
                log.warn("Ingest chunk {} failed: {}", chunkId, e.getMessage());
            }
        }
        log.info("Graph ingestion done: {} ok, {} failed, {} entities, {} relations",
                chunks, failed, entities, relations);
        return new IngestionStats(chunks, entities, relations);
    }

    private static List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) {
            list.add(f);
        }
        return list;
    }
}
