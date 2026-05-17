package com.knowledge.assistant.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.assistant.common.model.KnowledgeDocument;
import com.knowledge.assistant.rag.service.EmbedResult;
import com.knowledge.assistant.rag.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {
    private static final String DOC_KEY_PREFIX = "doc:meta:";
    private static final String DOC_INDEX_KEY = "doc:index";

    private final EmbeddingService embeddingService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public KnowledgeDocument upload(byte[] content, String filename) {
        EmbedResult result = embeddingService.embed(content, filename);
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(result.docId());
        doc.setTitle(filename);
        doc.setSourceType(getExtension(filename));
        doc.setChunkCount(result.chunkCount());
        doc.setFileSize(content.length);
        doc.setCreateTime(LocalDateTime.now());

        saveToRedis(result.docId(), doc);
        log.info("Document uploaded: {} -> {} ({} chunks)", filename, result.docId(), result.chunkCount());
        return doc;
    }

    public void delete(String id) {
        embeddingService.deleteByDocId(id);
        redisTemplate.delete(DOC_KEY_PREFIX + id);
        redisTemplate.opsForSet().remove(DOC_INDEX_KEY, id);
        log.info("Document deleted: {}", id);
    }

    public void deleteBatch(List<String> ids) {
        ids.forEach(this::delete);
        log.info("Batch deleted {} documents", ids.size());
    }

    public List<KnowledgeDocument> list() {
        Set<String> docIds = redisTemplate.opsForSet().members(DOC_INDEX_KEY);
        if (docIds == null || docIds.isEmpty()) {
            return List.of();
        }
        List<KnowledgeDocument> docs = new ArrayList<>();
        for (String docId : docIds) {
            loadFromRedis(docId).ifPresent(docs::add);
        }
        docs.sort(Comparator.comparing(KnowledgeDocument::getCreateTime).reversed());
        return docs;
    }

    public Optional<KnowledgeDocument> getById(String id) {
        return loadFromRedis(id);
    }

    public long getTotalCount() {
        Set<String> docIds = redisTemplate.opsForSet().members(DOC_INDEX_KEY);
        return docIds != null ? docIds.size() : 0;
    }

    public long getTotalChunks() {
        return list().stream()
                .mapToLong(KnowledgeDocument::getChunkCount)
                .sum();
    }

    private void saveToRedis(String docId, KnowledgeDocument doc) {
        try {
            String json = objectMapper.writeValueAsString(doc);
            redisTemplate.opsForValue().set(DOC_KEY_PREFIX + docId, json);
            redisTemplate.opsForSet().add(DOC_INDEX_KEY, docId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize document {}", docId, e);
            throw new RuntimeException("Failed to save document metadata", e);
        }
    }

    private Optional<KnowledgeDocument> loadFromRedis(String docId) {
        String json = redisTemplate.opsForValue().get(DOC_KEY_PREFIX + docId);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, KnowledgeDocument.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize document {}", docId, e);
            return Optional.empty();
        }
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "unknown";
    }
}
