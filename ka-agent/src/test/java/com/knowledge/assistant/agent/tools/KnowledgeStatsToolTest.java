package com.knowledge.assistant.agent.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class KnowledgeStatsToolTest {
    @Test
    void shouldReturnNonBlankStats() {
        assertThat(new KnowledgeStatsTool().getStats()).isNotBlank();
    }
}
