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

    private static final int CHUNK_SIZE = 800;
    private static final int MIN_CHUNK_SIZE_CHARS = 200;

    private final TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(CHUNK_SIZE)
            .withMinChunkSizeChars(MIN_CHUNK_SIZE_CHARS)
            .build();

    /**
     * Split a list of documents into smaller chunks.
     *
     * @param documents the documents to split
     * @return list of split document chunks
     */
    public List<Document> split(List<Document> documents) {
        return splitter.apply(documents);
    }
}
