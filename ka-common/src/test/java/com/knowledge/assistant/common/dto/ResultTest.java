package com.knowledge.assistant.common.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ResultTest {
    @Test
    void okReturnsSuccess() {
        Result<String> r = Result.ok("hello");
        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData()).isEqualTo("hello");
        assertThat(r.isSuccess()).isTrue();
    }

    @Test
    void failReturnsError() {
        Result<Void> r = Result.fail("error msg");
        assertThat(r.getCode()).isEqualTo(500);
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getMessage()).isEqualTo("error msg");
    }

    @Test
    void okWithNullData() {
        Result<Void> r = Result.ok(null);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getData()).isNull();
    }
}
