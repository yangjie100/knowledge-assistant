package com.knowledge.assistant.rag.service;

import com.knowledge.assistant.rag.loader.DocumentLoaderFactory;
import com.knowledge.assistant.rag.splitter.DocumentSplitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {
    private final VectorStore vectorStore;
    private final DocumentLoaderFactory loaderFactory;
    private final DocumentSplitter splitter;

    private final ConcurrentHashMap<String, List<String>> docChunkMap = new ConcurrentHashMap<>();

    public String embed(byte[] content, String filename) {
        log.info("Embedding document: {}", filename);
        List<Document> rawDocs = loaderFactory.load(content, filename);
        List<Document> chunks = splitter.split(rawDocs);
        String docId = UUID.randomUUID().toString();
        chunks.forEach(chunk -> chunk.getMetadata().put("docId", docId));
        vectorStore.add(chunks);
        List<String> chunkIds = chunks.stream().map(Document::getId).toList();
        docChunkMap.put(docId, chunkIds);
        log.info("Embedded {} chunks for doc {}", chunks.size(), docId);
        return docId;
    }

    public void deleteByDocId(String docId) {
        log.info("Deleting document vectors: {}", docId);
        List<String> chunkIds = docChunkMap.remove(docId);
        if (chunkIds != null && !chunkIds.isEmpty()) {
            vectorStore.delete(chunkIds);
            log.info("Deleted {} chunks for doc {}", chunkIds.size(), docId);
        } else {
            log.warn("No chunks found for docId: {}", docId);
        }
    }
}
