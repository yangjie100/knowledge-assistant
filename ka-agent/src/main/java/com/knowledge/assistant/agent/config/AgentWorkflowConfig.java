package com.knowledge.assistant.agent.config;

import com.knowledge.assistant.agent.workflow.*;
import com.knowledge.assistant.agent.workflow.impl.ChainWorkflow;
import com.knowledge.assistant.agent.workflow.impl.ParallelizationWorkflow;
import com.knowledge.assistant.agent.workflow.impl.RoutingWorkflow;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Agent workflow configuration: pre-built workflow factory methods
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AgentWorkflowConfig {

    private final ChatClient chatClient;

    /**
     * Create a ChatClient-based workflow step
     */
    public WorkflowStep createStep(String name, String systemPrompt) {
        return new ChatClientStep(chatClient, name, systemPrompt);
    }

    /**
     * Create a chain workflow with the given steps
     */
    public Workflow createChainWorkflow(List<WorkflowStep> steps) {
        return new ChainWorkflow(steps);
    }

    /**
     * Create a parallel workflow with the given steps
     */
    public Workflow createParallelWorkflow(List<WorkflowStep> steps) {
        return new ParallelizationWorkflow(steps);
    }

    /**
     * Create a routing workflow with the given step map
     */
    public Workflow createRoutingWorkflow(Map<String, WorkflowStep> stepMap) {
        WorkflowStep router = new RouterSelectorStep(chatClient, stepMap, "AI Router");
        return new RoutingWorkflow(router, stepMap);
    }

    /**
     * Build a pre-configured chain workflow for content generation
     */
    public Workflow buildContentChain() {
        List<WorkflowStep> steps = List.of(
                createStep("outline", "Generate a detailed outline based on the given topic, including introduction, main sections, and conclusion."),
                createStep("expand", "Based on the provided outline, write a complete article."),
                createStep("polish", "Polish and optimize the provided article, improving language expression and readability.")
        );
        return createChainWorkflow(steps);
    }

    /**
     * Build a pre-configured parallel workflow for multi-perspective review
     */
    public Workflow buildReviewParallel() {
        List<WorkflowStep> steps = List.of(
                createStep("marketing", "Evaluate the product from a marketing perspective, including market positioning and target audience analysis."),
                createStep("product", "Evaluate the product from a product management perspective, analyzing product positioning and features."),
                createStep("ux", "Evaluate the product from a UX perspective, analyzing interface design and interaction flow.")
        );
        return createParallelWorkflow(steps);
    }

    /**
     * Build a pre-configured routing workflow for customer service
     */
    public Workflow buildCustomerServiceRouting() {
        Map<String, WorkflowStep> stepMap = new LinkedHashMap<>();
        stepMap.put("billing", createStep("billing", "Handle billing inquiries. Answer in Chinese."));
        stepMap.put("technical", createStep("technical", "Handle technical support questions. Answer in Chinese."));
        stepMap.put("product", createStep("product", "Handle product information questions. Answer in Chinese."));
        stepMap.put("general", createStep("general", "Handle general questions. Answer in Chinese."));
        return createRoutingWorkflow(stepMap);
    }

    @PostConstruct
    public void logInit() {
        log.info("AgentWorkflowConfig initialized with ChatClient: {}", chatClient.getClass().getSimpleName());
    }
}
