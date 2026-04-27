package com.knowledge.assistant.rag.splitter;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Splits documents into smaller chunks using token-based splitting.
 */
@Component
public class DocumentSplitter {

    private static final int DEFAULT_CHUNK_SIZE = 800;
    private static final int DEFAULT_OVERLAP = 200;

    /**
     * Split a list of documents into smaller chunks.
     *
     * @param documents the documents to split
     * @return list of split document chunks
     */
    public List<Document> split(List<Document> documents) {
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(DEFAULT_CHUNK_SIZE)
                .withMinChunkSizeChars(DEFAULT_OVERLAP)
                .build();
        return splitter.apply(documents);
    }
}
