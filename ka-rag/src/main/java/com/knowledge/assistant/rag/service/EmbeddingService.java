package com.knowledge.assistant.rag.service;

import com.knowledge.assistant.rag.loader.DocumentLoaderFactory;
import com.knowledge.assistant.rag.splitter.DocumentSplitter;
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

    private final VectorStore vectorStore;
    private final DocumentLoaderFactory loaderFactory;
    private final DocumentSplitter splitter;
    private final StringRedisTemplate redisTemplate;

    public EmbedResult embed(byte[] content, String filename) {
        log.info("Embedding document: {}", filename);
        List<Document> rawDocs = loaderFactory.load(content, filename);
        List<Document> chunks = splitter.split(rawDocs);
        String docId = UUID.randomUUID().toString();
        chunks.forEach(chunk -> chunk.getMetadata().put("docId", docId));
        vectorStore.add(chunks);
        List<String> chunkIds = chunks.stream().map(Document::getId).toList();
        redisTemplate.opsForSet().add(CHUNK_KEY_PREFIX + docId,
                chunkIds.toArray(new String[0]));
        log.info("Embedded {} chunks for doc {}", chunks.size(), docId);
        return new EmbedResult(docId, chunks.size());
    }

    public void deleteByDocId(String docId) {
        log.info("Deleting document vectors: {}", docId);
        String key = CHUNK_KEY_PREFIX + docId;
        Set<String> chunkIds = redisTemplate.opsForSet().members(key);
        if (chunkIds != null && !chunkIds.isEmpty()) {
            vectorStore.delete(List.copyOf(chunkIds));
            log.info("Deleted {} chunks for doc {}", chunkIds.size(), docId);
        } else {
            log.warn("No chunks found for docId: {}", docId);
        }
        redisTemplate.delete(key);
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
