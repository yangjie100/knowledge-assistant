package com.knowledge.assistant.agent.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CalculatorToolTest {
    private final CalculatorTool tool = new CalculatorTool();

    @Test
    @DisplayName("should add two numbers")
    void shouldAdd() {
        assertThat(tool.calculate("2+3")).contains("5");
    }

    @Test
    @DisplayName("should respect operator precedence")
    void shouldRespectPrecedence() {
        assertThat(tool.calculate("2+3*4")).contains("14");
    }

    @Test
    @DisplayName("should handle parentheses")
    void shouldHandleParentheses() {
        assertThat(tool.calculate("(2+3)*4")).contains("20");
    }

    @Test
    @DisplayName("should handle division")
    void shouldHandleDivision() {
        assertThat(tool.calculate("10/3")).contains("3.333");
    }

    @Test
    @DisplayName("should reject alphabetic input")
    void shouldRejectAlphabetic() {
        assertThatThrownBy(() -> tool.calculate("abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject code injection")
    void shouldRejectInjection() {
        assertThatThrownBy(() -> tool.calculate("1;System.exit(0)")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should handle division by zero gracefully")
    void shouldHandleDivisionByZero() {
        assertThat(tool.calculate("1/0")).containsAnyOf("Error", "Infinity", "error");
    }
}
