package com.knowledge.assistant.rag.loader;

import org.springframework.ai.document.Document;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Loads Markdown (.md) files into Document objects.
 */
public class MarkdownDocumentLoader implements DocumentLoader {

    @Override
    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".md");
    }

    @Override
    public List<Document> load(byte[] content, String filename) {
        String text = new String(content, StandardCharsets.UTF_8);
        Document doc = new Document(text, Map.of("source", filename, "type", "md"));
        return List.of(doc);
    }
}
