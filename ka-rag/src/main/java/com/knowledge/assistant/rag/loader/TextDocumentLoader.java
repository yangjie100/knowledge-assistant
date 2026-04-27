package com.knowledge.assistant.rag.loader;

import org.springframework.ai.document.Document;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Loads plain text (.txt) files into Document objects.
 */
public class TextDocumentLoader implements DocumentLoader {

    @Override
    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".txt");
    }

    @Override
    public List<Document> load(byte[] content, String filename) {
        String text = new String(content, StandardCharsets.UTF_8);
        Document doc = new Document(text, Map.of("source", filename, "type", "txt"));
        return List.of(doc);
    }
}
