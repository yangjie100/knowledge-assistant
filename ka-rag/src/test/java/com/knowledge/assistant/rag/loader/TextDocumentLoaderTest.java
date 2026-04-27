package com.knowledge.assistant.rag.loader;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TextDocumentLoaderTest {

    private final TextDocumentLoader loader = new TextDocumentLoader();

    @Test
    void supportsTxtFile() {
        assertThat(loader.supports("test.txt")).isTrue();
        assertThat(loader.supports("README.TXT")).isTrue();
        assertThat(loader.supports("test.pdf")).isFalse();
        assertThat(loader.supports("test.md")).isFalse();
    }

    @Test
    void supportsNullFilename() {
        assertThat(loader.supports(null)).isFalse();
    }

    @Test
    void loadTextContent() {
        byte[] content = "Hello World".getBytes(StandardCharsets.UTF_8);
        var docs = loader.load(content, "test.txt");

        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getText()).isEqualTo("Hello World");
        assertThat(docs.get(0).getMetadata()).containsEntry("source", "test.txt");
        assertThat(docs.get(0).getMetadata()).containsEntry("type", "txt");
    }
}
