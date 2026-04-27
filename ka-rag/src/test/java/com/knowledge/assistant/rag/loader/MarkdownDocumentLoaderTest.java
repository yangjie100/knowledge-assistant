package com.knowledge.assistant.rag.loader;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownDocumentLoaderTest {

    private final MarkdownDocumentLoader loader = new MarkdownDocumentLoader();

    @Test
    void supportsMdFile() {
        assertThat(loader.supports("readme.md")).isTrue();
        assertThat(loader.supports("README.MD")).isTrue();
        assertThat(loader.supports("test.txt")).isFalse();
        assertThat(loader.supports(null)).isFalse();
    }

    @Test
    void loadMarkdownContent() {
        String markdown = "# Title\n\nThis is **bold** text.";
        byte[] content = markdown.getBytes(StandardCharsets.UTF_8);
        var docs = loader.load(content, "readme.md");

        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getText()).isEqualTo(markdown);
        assertThat(docs.get(0).getMetadata()).containsEntry("source", "readme.md");
        assertThat(docs.get(0).getMetadata()).containsEntry("type", "md");
    }
}
