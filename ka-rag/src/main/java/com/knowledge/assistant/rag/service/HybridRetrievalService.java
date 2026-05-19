package com.knowledge.assistant.rag.service;

import com.knowledge.assistant.rag.util.ContentHashUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.util.SafeEncoder;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRetrievalService {

    private static final String CACHE_PREFIX = "cache:retrieval:";
    private static final String CHUNK_TEXT_PREFIX = "chunk:text:";
    private static final int TOP_K = 5;
    private static final double SIMILARITY_THRESHOLD = 0.7;
    private static final int RRF_K = 60;

    private final VectorStore vectorStore;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public List<Document> hybridRetrieve(String query) {
        log.info("Hybrid retrieval for query: {}", query);
        List<CachedResult> cached = getFromCache(query);
        if (cached != null) {
            log.info("Cache hit for query");
            return reconstructDocuments(cached);
        }
        List<Document> vectorResults = vectorStore.similaritySearch(
            SearchRequest.builder().query(query).topK(TOP_K)
                .similarityThreshold(SIMILARITY_THRESHOLD).build());
        List<KeywordResult> keywordResults = keywordSearch(query);
        List<Document> fused = rrfFusion(vectorResults, keywordResults);
        saveToCache(query, fused);
        return fused;
    }

    List<KeywordResult> keywordSearch(String query) {
        try {
            Object result = redisTemplate.execute((RedisCallback<Object>) (connection) -> {
                Jedis jedis = (Jedis) connection.getNativeConnection();
                return jedis.sendCommand(
                    () -> SafeEncoder.encode("FT.SEARCH"),
                    SafeEncoder.encode("chunk-idx"),
                    SafeEncoder.encode(query),
                    SafeEncoder.encode("LIMIT"),
                    SafeEncoder.encode("0"),
                    SafeEncoder.encode(String.valueOf(TOP_K)));
            });
            if (result == null) return Collections.emptyList();
            return parseFtSearchResult(result);
        } catch (Exception e) {
            log.warn("Keyword search failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<KeywordResult> parseFtSearchResult(Object result) {
        List<KeywordResult> results = new ArrayList<>();
        if (!(result instanceof List<?> list)) return results;
        for (int i = 1; i < list.size(); i += 2) {
            if (i + 1 >= list.size()) break;
            Object idObj = list.get(i);
            Object fieldsObj = list.get(i + 1);
            String chunkId = idObj instanceof byte[] ? new String((byte[]) idObj) : idObj.toString();
            if (fieldsObj instanceof List<?> fields) {
                String content = "";
                String docId = "";
                for (int j = 0; j < fields.size() - 1; j += 2) {
                    String fn = fields.get(j) instanceof byte[] ? new String((byte[]) fields.get(j)) : fields.get(j).toString();
                    String fv = fields.get(j + 1) instanceof byte[] ? new String((byte[]) fields.get(j + 1)) : fields.get(j + 1).toString();
                    if ("content".equals(fn)) content = fv;
                    if ("docId".equals(fn)) docId = fv;
                }
                if (!content.isEmpty()) results.add(new KeywordResult(chunkId, content, docId));
            }
        }
        return results;
    }

    List<Document> rrfFusion(List<Document> vectorResults, List<KeywordResult> keywordResults) {
        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, Document> docMap = new HashMap<>();
        for (int i = 0; i < vectorResults.size(); i++) {
            Document doc = vectorResults.get(i);
            String id = doc.getId();
            rrfScores.merge(id, 1.0 / (RRF_K + i + 1), Double::sum);
            docMap.put(id, doc);
        }
        for (int i = 0; i < keywordResults.size(); i++) {
            KeywordResult kr = keywordResults.get(i);
            String id = kr.chunkId;
            rrfScores.merge(id, 1.0 / (RRF_K + i + 1), Double::sum);
            if (!docMap.containsKey(id)) {
                docMap.put(id, new Document(id, kr.content, Map.of("docId", kr.docId, "source", "keyword")));
            }
        }
        return rrfScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(TOP_K)
            .map(e -> { Document doc = docMap.get(e.getKey()); doc.getMetadata().put("rrfScore", e.getValue()); return doc; })
            .collect(Collectors.toList());
    }

    private List<CachedResult> getFromCache(String query) {
        try {
            String cacheKey = CACHE_PREFIX + ContentHashUtil.sha256(query.getBytes());
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached == null) return null;
            return objectMapper.readValue(cached, objectMapper.getTypeFactory().constructCollectionType(List.class, CachedResult.class));
        } catch (Exception e) { log.warn("Cache read failed: {}", e.getMessage()); return null; }
    }

    private void saveToCache(String query, List<Document> docs) {
        try {
            String cacheKey = CACHE_PREFIX + ContentHashUtil.sha256(query.getBytes());
            List<CachedResult> results = docs.stream()
                .map(d -> new CachedResult(d.getId(), d.getMetadata().get("docId") instanceof String s ? s : "", d.getText(), d.getMetadata().get("rrfScore") instanceof Number n ? n.doubleValue() : 0.0))
                .toList();
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(results), java.time.Duration.ofSeconds(300));
        } catch (Exception e) { log.warn("Cache write failed: {}", e.getMessage()); }
    }

    private List<Document> reconstructDocuments(List<CachedResult> cached) {
        return cached.stream().map(c -> new Document(c.chunkId, c.content, Map.of("docId", c.docId, "rrfScore", c.score))).collect(Collectors.toList());
    }

    public void clearCache() {
        Set<String> keys = redisTemplate.keys(CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) { redisTemplate.delete(keys); log.info("Cleared {} retrieval cache entries", keys.size()); }
    }

    public void ensureFtIndex() {
        try {
            redisTemplate.execute((RedisCallback<Void>) (connection) -> {
                Jedis jedis = (Jedis) connection.getNativeConnection();
                jedis.sendCommand(
                    () -> SafeEncoder.encode("FT.CREATE"),
                    SafeEncoder.encode("chunk-idx"),
                    SafeEncoder.encode("ON"),
                    SafeEncoder.encode("HASH"),
                    SafeEncoder.encode("PREFIX"),
                    SafeEncoder.encode("1"),
                    SafeEncoder.encode(CHUNK_TEXT_PREFIX),
                    SafeEncoder.encode("SCHEMA"),
                    SafeEncoder.encode("content"),
                    SafeEncoder.encode("TEXT"),
                    SafeEncoder.encode("docId"),
                    SafeEncoder.encode("TAG"));
                return null;
            });
            log.info("Created FT index: chunk-idx");
        } catch (Exception e) { log.debug("FT index may already exist: {}", e.getMessage()); }
    }

    record KeywordResult(String chunkId, String content, String docId) {}
    record CachedResult(String chunkId, String docId, String content, double score) {}
}
