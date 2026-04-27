package com.knowledge.assistant.rag.splitter;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentSplitterTest {

    private final DocumentSplitter splitter = new DocumentSplitter();

    @Test
    void shortTextStaysAsSingleChunk() {
        Document doc = new Document("Hello World", Map.of("source", "test.txt"));
        List<Document> chunks = splitter.split(List.of(doc));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getText()).isEqualTo("Hello World");
    }

    @Test
    void longTextProducesMultipleChunks() {
        // Generate text that exceeds the 800-token chunk size
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("This is sentence number ").append(i)
                    .append(" in a long document used for testing the splitter. ");
        }
        Document doc = new Document(sb.toString(), Map.of("source", "long.txt"));
        List<Document> chunks = splitter.split(List.of(doc));

        assertThat(chunks.size()).isGreaterThan(1);
        // Verify all chunks have content
        for (Document chunk : chunks) {
            assertThat(chunk.getText()).isNotBlank();
        }
    }

    @Test
    void multipleDocumentsAreAllSplit() {
        Document doc1 = new Document("Short document one.", Map.of("source", "a.txt"));
        Document doc2 = new Document("Short document two.", Map.of("source", "b.txt"));
        List<Document> chunks = splitter.split(List.of(doc1, doc2));

        assertThat(chunks).hasSize(2);
    }
}
