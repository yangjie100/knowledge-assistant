package com.knowledge.assistant.rag.loader;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Strategy interface for loading documents from raw byte content.
 */
public interface DocumentLoader {

    /**
     * Check if this loader supports the given filename.
     *
     * @param filename the filename to check
     * @return true if this loader can handle the file type
     */
    boolean supports(String filename);

    /**
     * Load documents from raw byte content.
     *
     * @param content  the raw file content
     * @param filename the original filename
     * @return list of parsed documents
     */
    List<Document> load(byte[] content, String filename);
}
