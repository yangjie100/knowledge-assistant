package com.knowledge.assistant.rag.loader;

import com.knowledge.assistant.common.exception.DocumentParseException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentLoaderFactoryTest {

    private final DocumentLoaderFactory factory = new DocumentLoaderFactory();

    @Test
    void loadTxtFile() {
        byte[] content = "Hello World".getBytes(StandardCharsets.UTF_8);
        var docs = factory.load(content, "test.txt");

        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getText()).isEqualTo("Hello World");
    }

    @Test
    void loadMdFile() {
        byte[] content = "# Title".getBytes(StandardCharsets.UTF_8);
        var docs = factory.load(content, "readme.md");

        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getText()).isEqualTo("# Title");
    }

    @Test
    void unsupportedFileType() {
        byte[] content = "data".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> factory.load(content, "data.xlsx"))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("Unsupported file type");
    }
}
