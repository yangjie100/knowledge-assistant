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
import java.util.stream.Collectors;

/**
 * Parallel workflow: all steps execute concurrently with the same input, results are merged
 */
@Slf4j
public class ParallelizationWorkflow implements Workflow {

    private final List<WorkflowStep> steps;

    public ParallelizationWorkflow(List<WorkflowStep> steps) {
        this.steps = steps;
    }

    @Override
    public WorkflowResponse execute(WorkflowRequest input) {
        try {
            log.info("Parallel workflow started, step count: {}", steps.size());

            List<CompletableFuture<Map.Entry<String, String>>> futures = new ArrayList<>();

            for (WorkflowStep step : steps) {
                CompletableFuture<Map.Entry<String, String>> future = CompletableFuture.supplyAsync(() -> {
                    log.info("Executing step: {}", step.name());
                    String result = step.execute(input.question());
                    return Map.entry(step.name(), result);
                });
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

            Map<String, String> results = resultFuture.get();
            String content = formatResults(results);
            log.info("Parallel workflow completed, result count: {}", results.size());

            return WorkflowResponse.builder()
                    .success(true)
                    .content(content)
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
