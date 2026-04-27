package com.knowledge.assistant.agent.workflow.impl;

import com.knowledge.assistant.agent.workflow.WorkflowRequest;
import com.knowledge.assistant.agent.workflow.WorkflowResponse;
import com.knowledge.assistant.agent.workflow.WorkflowStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChainWorkflowTest {

    @Test
    @DisplayName("Chain workflow should pass output of each step to the next")
    void shouldExecuteStepsSequentially() {
        // Given
        WorkflowStep step1 = new StubStep("uppercase", String::toUpperCase);
        WorkflowStep step2 = new StubStep("add-prefix", s -> "PREFIX:" + s);
        WorkflowStep step3 = new StubStep("add-suffix", s -> s + ":SUFFIX");

        List<WorkflowStep> steps = Arrays.asList(step1, step2, step3);
        ChainWorkflow workflow = new ChainWorkflow(steps);

        // When
        WorkflowResponse response = workflow.execute(new WorkflowRequest("hello"));

        // Then
        assertTrue(response.isSuccess());
        assertEquals("PREFIX:HELLO:SUFFIX", response.getContent());
    }

    @Test
    @DisplayName("Chain workflow should return error when step throws exception")
    void shouldReturnErrorOnException() {
        // Given
        WorkflowStep failingStep = new StubStep("fail", s -> {
            throw new RuntimeException("Step failed");
        });

        ChainWorkflow workflow = new ChainWorkflow(List.of(failingStep));

        // When
        WorkflowResponse response = workflow.execute(new WorkflowRequest("test"));

        // Then
        assertFalse(response.isSuccess());
        assertNotNull(response.getErrorMessage());
        assertTrue(response.getErrorMessage().contains("Step failed"));
    }

    @Test
    @DisplayName("Chain workflow with single step should work")
    void shouldWorkWithSingleStep() {
        // Given
        WorkflowStep step = new StubStep("echo", s -> s + "-echo");
        ChainWorkflow workflow = new ChainWorkflow(List.of(step));

        // When
        WorkflowResponse response = workflow.execute(new WorkflowRequest("hello"));

        // Then
        assertTrue(response.isSuccess());
        assertEquals("hello-echo", response.getContent());
    }

    /**
     * Stub implementation of WorkflowStep for testing
     */
    static class StubStep implements WorkflowStep {
        private final String stepName;
        private final java.util.function.Function<String, String> fn;

        StubStep(String stepName, java.util.function.Function<String, String> fn) {
            this.stepName = stepName;
            this.fn = fn;
        }

        @Override
        public String execute(String input) {
            return fn.apply(input);
        }

        @Override
        public String name() {
            return stepName;
        }
    }
}
