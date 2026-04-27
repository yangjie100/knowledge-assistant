package com.knowledge.assistant.rag.loader;

import com.knowledge.assistant.common.exception.DocumentParseException;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Factory that selects the appropriate DocumentLoader based on filename extension.
 */
public class DocumentLoaderFactory {

    private final List<DocumentLoader> loaders;

    public DocumentLoaderFactory() {
        this.loaders = List.of(
                new TextDocumentLoader(),
                new MarkdownDocumentLoader(),
                new PdfDocumentLoader(),
                new HtmlDocumentLoader()
        );
    }

    /**
     * Load documents from raw byte content by selecting the matching loader.
     *
     * @param content  the raw file content
     * @param filename the original filename used to determine the loader
     * @return list of parsed documents
     * @throws DocumentParseException if the file type is not supported
     */
    public List<Document> load(byte[] content, String filename) {
        return loaders.stream()
                .filter(loader -> loader.supports(filename))
                .findFirst()
                .orElseThrow(() -> new DocumentParseException("Unsupported file type: " + filename))
                .load(content, filename);
    }
}
