package com.knowledge.assistant.rag.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContentHashUtilTest {

    @Test
    void sameContentProducesSameHash() {
        byte[] content = "Hello World".getBytes();
        String hash1 = ContentHashUtil.sha256(content);
        String hash2 = ContentHashUtil.sha256(content);
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length());
    }

    @Test
    void differentContentProducesDifferentHash() {
        String hash1 = ContentHashUtil.sha256("Hello".getBytes());
        String hash2 = ContentHashUtil.sha256("World".getBytes());
        assertNotEquals(hash1, hash2);
    }
}
