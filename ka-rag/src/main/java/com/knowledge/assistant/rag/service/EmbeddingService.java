package com.knowledge.assistant.rag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.assistant.rag.loader.DocumentLoaderFactory;
import com.knowledge.assistant.rag.splitter.DocumentSplitter;
import com.knowledge.assistant.rag.util.ContentHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {
    private static final String CHUNK_KEY_PREFIX = "doc:chunks:";
    private static final String HASH_KEY_PREFIX = "doc:hash:";
    private static final String CHUNK_TEXT_PREFIX = "chunk:text:";
    private static final String META_KEY_PREFIX = "doc:meta:";

    private final VectorStore vectorStore;
    private final DocumentLoaderFactory loaderFactory;
    private final DocumentSplitter splitter;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public EmbedResult embed(byte[] content, String filename) {
        log.info("Embedding document: {}", filename);

        String contentHash = ContentHashUtil.sha256(content);
        String hashKey = HASH_KEY_PREFIX + contentHash;

        String existingDocId = redisTemplate.opsForValue().get(hashKey);
        if (existingDocId != null) {
            log.info("Duplicate document detected: {} (existing docId: {})", filename, existingDocId);
            return new EmbedResult(existingDocId, 0, true);
        }

        List<Document> rawDocs = loaderFactory.load(content, filename);
        List<Document> chunks = splitter.split(rawDocs);
        String docId = UUID.randomUUID().toString();
        chunks.forEach(chunk -> chunk.getMetadata().put("docId", docId));
        vectorStore.add(chunks);

        // Store chunk text in Redis Hash for full-text search
        for (Document chunk : chunks) {
            String chunkTextKey = CHUNK_TEXT_PREFIX + chunk.getId();
                redisTemplate.opsForHash().put(chunkTextKey, "content", chunk.getText());
                redisTemplate.opsForHash().put(chunkTextKey, "docId", docId);
        }

        List<String> chunkIds = chunks.stream().map(Document::getId).toList();
        redisTemplate.opsForSet().add(CHUNK_KEY_PREFIX + docId,
                chunkIds.toArray(new String[0]));
        redisTemplate.opsForValue().set(hashKey, docId);

        log.info("Embedded {} chunks for doc {}", chunks.size(), docId);
        return new EmbedResult(docId, chunks.size(), false);
    }

    public void deleteByDocId(String docId) {
        log.info("Deleting document vectors: {}", docId);
        // H-2 fix: drop the content-hash index key BEFORE anything else (reading it from
        // doc:meta while it still exists), otherwise re-uploading identical content hits
        // the stale hash key and is forever flagged DUPLICATE with chunkCount=0.
        String contentHash = readContentHash(docId);
        if (contentHash != null) {
            redisTemplate.delete(HASH_KEY_PREFIX + contentHash);
        }
        String key = CHUNK_KEY_PREFIX + docId;
        Set<String> chunkIds = redisTemplate.opsForSet().members(key);
        if (chunkIds != null && !chunkIds.isEmpty()) {
            vectorStore.delete(List.copyOf(chunkIds));
            for (String chunkId : chunkIds) {
                redisTemplate.delete(CHUNK_TEXT_PREFIX + chunkId);
            }
            log.info("Deleted {} chunks for doc {}", chunkIds.size(), docId);
        } else {
            log.warn("No chunks found for docId: {}", docId);
        }
        redisTemplate.delete(key);
    }

    private String readContentHash(String docId) {
        String json = redisTemplate.opsForValue().get(META_KEY_PREFIX + docId);
        if (json == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json).path("contentHash");
            return node.isTextual() ? node.asText() : null;
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse doc meta for {}, skipping hash cleanup", docId, e);
            return null;
        }
    }

    public int getChunkCount(String docId) {
        Long size = redisTemplate.opsForSet().size(CHUNK_KEY_PREFIX + docId);
        return size != null ? size.intValue() : 0;
    }

    public int getTotalDocumentCount() {
        Set<String> keys = redisTemplate.keys(CHUNK_KEY_PREFIX + "*");
        return keys != null ? keys.size() : 0;
    }
}
