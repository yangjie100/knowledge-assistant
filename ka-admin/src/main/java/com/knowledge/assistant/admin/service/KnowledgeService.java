package com.knowledge.assistant.admin.service;

import com.knowledge.assistant.common.model.KnowledgeDocument;
import com.knowledge.assistant.rag.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {
    private final EmbeddingService embeddingService;
    private final Map<String, KnowledgeDocument> store = new ConcurrentHashMap<>();

    public KnowledgeDocument upload(byte[] content, String filename) {
        String docId = embeddingService.embed(content, filename);
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(docId);
        doc.setTitle(filename);
        doc.setSourceType(getExtension(filename));
        doc.setCreateTime(LocalDateTime.now());
        store.put(docId, doc);
        log.info("Document uploaded: {} -> {}", filename, docId);
        return doc;
    }

    public void delete(String id) {
        embeddingService.deleteByDocId(id);
        store.remove(id);
        log.info("Document deleted: {}", id);
    }

    public List<KnowledgeDocument> list() {
        return new ArrayList<>(store.values());
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "unknown";
    }
}
