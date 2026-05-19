package com.knowledge.assistant.rag.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LanguageDetectorTest {

    @Test
    void detectsChinese() {
        assertEquals("zh", LanguageDetector.detect("这是一个中文文本，用来测试语言检测功能"));
    }

    @Test
    void detectsEnglish() {
        assertEquals("en", LanguageDetector.detect("This is an English text for testing language detection"));
    }

    @Test
    void handlesEmptyInput() {
        assertEquals("unknown", LanguageDetector.detect(""));
        assertEquals("unknown", LanguageDetector.detect(null));
    }

    @Test
    void detectsMixedContent() {
        String result = LanguageDetector.detect("Hello世界Hello世界Hello世界");
        assertNotNull(result);
        assertTrue(result.equals("zh") || result.equals("en"));
    }
}
