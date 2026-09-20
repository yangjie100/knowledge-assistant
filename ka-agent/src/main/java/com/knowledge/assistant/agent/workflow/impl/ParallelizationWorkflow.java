package com.knowledge.assistant.agent.workflow.impl;

import com.knowledge.assistant.agent.workflow.Workflow;
import com.knowledge.assistant.agent.workflow.WorkflowRequest;
import com.knowledge.assistant.agent.workflow.WorkflowResponse;
import com.knowledge.assistant.agent.workflow.WorkflowStep;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Parallel workflow: all steps execute concurrently with the same input, results are merged.
 * Implements the Parallelization (sectioning / voting) pattern (Anthropic, "Building Effective Agents").
 */
@Slf4j
public class ParallelizationWorkflow implements Workflow {

    /** Aligned with the reranker HTTP timeout: a stuck LLM call must degrade, not hang forever. */
    private static final long DEFAULT_TIMEOUT_SECONDS = 120;

    private final List<WorkflowStep> steps;
    private final Executor executor;
    private final long timeoutSeconds;

    /** Convenience constructor (tests / non-Spring callers): common pool + default timeout. */
    public ParallelizationWorkflow(List<WorkflowStep> steps) {
        this(steps, ForkJoinPool.commonPool(), DEFAULT_TIMEOUT_SECONDS);
    }

    /** Production constructor with default timeout. */
    public ParallelizationWorkflow(List<WorkflowStep> steps, Executor executor) {
        this(steps, executor, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Production constructor: steps run blocking LLM IO, so a Spring-managed bounded executor
     * must be supplied (see AgentWorkflowConfig#agentWorkflowExecutor), never the common pool.
     */
    public ParallelizationWorkflow(List<WorkflowStep> steps, Executor executor, long timeoutSeconds) {
        this.steps = steps;
        this.executor = executor;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public WorkflowResponse execute(WorkflowRequest input) {
        List<CompletableFuture<Map.Entry<String, String>>> futures = new ArrayList<>();
        try {
            log.info("Parallel workflow started, step count: {}", steps.size());

            for (WorkflowStep step : steps) {
                CompletableFuture<Map.Entry<String, String>> future = CompletableFuture.supplyAsync(() -> {
                    log.info("Executing step: {}", step.name());
                    String result = step.execute(input.question());
                    return Map.entry(step.name(), result);
                }, executor);
                futures.add(future);
            }

            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );

            CompletableFuture<Map<String, String>> resultFuture = allFutures.thenApply(v ->
                    futures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
            );

            Map<String, String> results = resultFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            String content = formatResults(results);
            log.info("Parallel workflow completed, result count: {}", results.size());

            return WorkflowResponse.builder()
                    .success(true)
                    .content(content)
                    .build();

        } catch (TimeoutException e) {
            // Do not let a stuck LLM call hold the calling thread forever: cancel pending
            // steps (interrupt) and degrade to an error response at the timeout boundary.
            log.error("Parallel workflow timed out after {}s, degrading", timeoutSeconds);
            futures.forEach(f -> f.cancel(true));
            return WorkflowResponse.builder()
                    .success(false)
                    .errorMessage("Parallel workflow timed out after " + timeoutSeconds + "s")
                    .build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Parallel workflow failed", e);
            Thread.currentThread().interrupt();
            return WorkflowResponse.builder()
                    .success(false)
                    .errorMessage("Parallel workflow failed: " + e.getMessage())
                    .build();
        }
    }

    private String formatResults(Map<String, String> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("Parallel workflow results:\n");

        results.forEach((key, value) -> {
            sb.append("Step [").append(key).append("]:\n");
            sb.append(value != null ? value : "null").append("\n\n");
        });

        return sb.toString();
    }
}
