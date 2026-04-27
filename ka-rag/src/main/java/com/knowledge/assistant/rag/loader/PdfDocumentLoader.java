package com.knowledge.assistant.rag.loader;

import com.knowledge.assistant.common.exception.DocumentParseException;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.ByteArrayResource;

import java.util.List;

/**
 * Loads PDF files into Document objects using PagePdfDocumentReader.
 */
public class PdfDocumentLoader implements DocumentLoader {

    @Override
    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".pdf");
    }

    @Override
    public List<Document> load(byte[] content, String filename) {
        try {
            ByteArrayResource resource = new ByteArrayResource(content);
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.defaultConfig();
            PagePdfDocumentReader reader = new PagePdfDocumentReader(resource, config);
            return reader.get();
        } catch (Exception e) {
            throw new DocumentParseException("Failed to parse PDF file: " + filename, e);
        }
    }
}
