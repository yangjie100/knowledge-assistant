package com.knowledge.assistant.agent.workflow.impl;

import com.knowledge.assistant.agent.workflow.WorkflowRequest;
import com.knowledge.assistant.agent.workflow.WorkflowResponse;
import com.knowledge.assistant.agent.workflow.WorkflowStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParallelizationWorkflowTest {

    @Test
    @DisplayName("Parallel workflow should execute all steps concurrently and merge results")
    void shouldExecuteStepsInParallel() {
        // Given
        WorkflowStep step1 = new ChainWorkflowTest.StubStep("uppercase", String::toUpperCase);
        WorkflowStep step2 = new ChainWorkflowTest.StubStep("lowercase", String::toLowerCase);
        WorkflowStep step3 = new ChainWorkflowTest.StubStep("reverse", s -> new StringBuilder(s).reverse().toString());

        List<WorkflowStep> steps = Arrays.asList(step1, step2, step3);
        ParallelizationWorkflow workflow = new ParallelizationWorkflow(steps);

        // When
        WorkflowResponse response = workflow.execute(new WorkflowRequest("Hello"));

        // Then
        assertTrue(response.isSuccess());
        assertNotNull(response.getContent());
        assertTrue(response.getContent().contains("HELLO"));
        assertTrue(response.getContent().contains("hello"));
        assertTrue(response.getContent().contains("olleH"));
    }

    @Test
    @DisplayName("Parallel workflow should return error when a step throws exception")
    void shouldReturnErrorOnException() {
        // Given
        WorkflowStep failingStep = new ChainWorkflowTest.StubStep("fail", s -> {
            throw new RuntimeException("Parallel step failed");
        });

        ParallelizationWorkflow workflow = new ParallelizationWorkflow(List.of(failingStep));

        // When
        WorkflowResponse response = workflow.execute(new WorkflowRequest("test"));

        // Then
        assertFalse(response.isSuccess());
        assertNotNull(response.getErrorMessage());
        assertTrue(response.getErrorMessage().contains("Parallel step failed"));
    }

    @Test
    @DisplayName("Parallel workflow with single step should work")
    void shouldWorkWithSingleStep() {
        // Given
        WorkflowStep step = new ChainWorkflowTest.StubStep("echo", s -> s + "-echo");
        ParallelizationWorkflow workflow = new ParallelizationWorkflow(List.of(step));

        // When
        WorkflowResponse response = workflow.execute(new WorkflowRequest("test"));

        // Then
        assertTrue(response.isSuccess());
        assertTrue(response.getContent().contains("test-echo"));
    }

    @Test
    @DisplayName("Parallel workflow should degrade instead of hanging when a step exceeds timeout")
    void shouldDegradeOnTimeoutInsteadOfHanging() {
        // Given: a step blocked far longer than the 1s timeout, on a real async executor
        WorkflowStep slowStep = new ChainWorkflowTest.StubStep("slow", s -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return s;
        });
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            ParallelizationWorkflow workflow = new ParallelizationWorkflow(List.of(slowStep), executor, 1);

            // When
            long start = System.currentTimeMillis();
            WorkflowResponse response = workflow.execute(new WorkflowRequest("test"));
            long elapsed = System.currentTimeMillis() - start;

            // Then: degraded (not hanging), and returned roughly at the timeout boundary
            assertFalse(response.isSuccess());
            assertNotNull(response.getErrorMessage());
            assertTrue(elapsed < 3_000, "should degrade near the 1s timeout but took " + elapsed + "ms");
        } finally {
            executor.shutdownNow();
        }
    }
}
