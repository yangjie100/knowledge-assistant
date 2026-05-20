package com.knowledge.assistant.agent.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DateTimeToolTest {
    private final DateTimeTool tool = new DateTimeTool();

    @Test
    void shouldReturnNonNull() {
        assertThat(tool.getCurrentDateTime()).isNotBlank();
    }

    @Test
    void shouldContainDateAndTime() {
        String r = tool.getCurrentDateTime();
        assertThat(r).contains("-").contains(":");
    }
}
