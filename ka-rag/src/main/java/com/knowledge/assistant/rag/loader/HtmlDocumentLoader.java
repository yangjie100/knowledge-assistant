package com.knowledge.assistant.rag.loader;

import com.knowledge.assistant.common.exception.DocumentParseException;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.jsoup.JsoupDocumentReader;
import org.springframework.ai.reader.jsoup.config.JsoupDocumentReaderConfig;
import org.springframework.core.io.ByteArrayResource;

import java.util.List;

/**
 * Loads HTML files into Document objects using JsoupDocumentReader.
 */
public class HtmlDocumentLoader implements DocumentLoader {

    @Override
    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".html");
    }

    @Override
    public List<Document> load(byte[] content, String filename) {
        try {
            ByteArrayResource resource = new ByteArrayResource(content);
            JsoupDocumentReaderConfig config = JsoupDocumentReaderConfig.defaultConfig();
            JsoupDocumentReader reader = new JsoupDocumentReader(resource, config);
            return reader.get();
        } catch (Exception e) {
            throw new DocumentParseException("Failed to parse HTML file: " + filename, e);
        }
    }
}
