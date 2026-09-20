package com.knowledge.assistant.agent.config;

import com.knowledge.assistant.agent.workflow.*;
import com.knowledge.assistant.agent.workflow.impl.ChainWorkflow;
import com.knowledge.assistant.agent.workflow.impl.ParallelizationWorkflow;
import com.knowledge.assistant.agent.workflow.impl.RoutingWorkflow;
import com.knowledge.assistant.rag.service.RetrievalService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
public class AgentWorkflowConfig {

    private final ChatClient chatClient;
    private final RetrievalService retrievalService;

    /**
     * Injects the workflow-dedicated ChatClient by qualifier, NOT the @Primary chatClient
     * (which binds Ollama deepseek-r1 and took ~180s for long Chinese answers — the perf
     * bottleneck this fixes). Hand-written constructor instead of @RequiredArgsConstructor:
     * Lombok does not copy @Qualifier onto generated constructor params (no lombok.config
     * copyableAnnotations entry in this repo), so the qualifier would be silently dropped
     * and @Primary chatClient injected instead.
     */
    public AgentWorkflowConfig(@Qualifier("workflowChatClient") ChatClient chatClient,
                               RetrievalService retrievalService) {
        this.chatClient = chatClient;
        this.retrievalService = retrievalService;
    }

    public WorkflowStep createStep(String name, String systemPrompt) {
        return new ChatClientStep(chatClient, name, systemPrompt);
    }

    public WorkflowStep createSearchStep(String name) {
        return new KnowledgeSearchStep(retrievalService, name);
    }

    public WorkflowStep createContextAnswerStep(String name, String systemPrompt) {
        return new ContextAnswerStep(chatClient, name, systemPrompt);
    }

    public Workflow createChainWorkflow(List<WorkflowStep> steps) {
        return new ChainWorkflow(steps);
    }

    /**
     * Bounded Spring-managed pool for parallel workflow steps: they run blocking LLM IO,
     * which must not sit on ForkJoinPool.commonPool(). Sized for the largest parallel
     * workflow (3 steps) plus headroom; CallerRunsPolicy degrades by slowing submission
     * instead of failing the whole workflow when the bounded queue fills up.
     */
    @Bean
    public ThreadPoolTaskExecutor agentWorkflowExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(6);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("agent-wf-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    public Workflow createParallelWorkflow(List<WorkflowStep> steps) {
        return new ParallelizationWorkflow(steps, agentWorkflowExecutor());
    }

    public Workflow createRoutingWorkflow(Map<String, WorkflowStep> stepMap) {
        WorkflowStep router = new RouterSelectorStep(chatClient, stepMap, "AI Router");
        return new RoutingWorkflow(router, stepMap);
    }

    public Workflow buildContentChain() {
        List<WorkflowStep> steps = List.of(
                createStep("outline", "Generate a detailed outline based on the given topic, including introduction, main sections, and conclusion."),
                createStep("expand", "Based on the provided outline, write a complete article."),
                createStep("polish", "Polish and optimize the provided article, improving language expression and readability.")
        );
        return createChainWorkflow(steps);
    }

    public Workflow buildReviewParallel() {
        List<WorkflowStep> steps = List.of(
                createStep("marketing", "Evaluate the product from a marketing perspective, including market positioning and target audience analysis."),
                createStep("product", "Evaluate the product from a product management perspective, analyzing product positioning and features."),
                createStep("ux", "Evaluate the product from a UX perspective, analyzing interface design and interaction flow.")
        );
        return createParallelWorkflow(steps);
    }

    public Workflow buildCustomerServiceRouting() {
        Map<String, WorkflowStep> stepMap = new LinkedHashMap<>();
        stepMap.put("billing", createStep("billing", "Handle billing inquiries. Answer in Chinese."));
        stepMap.put("technical", createStep("technical", "Handle technical support questions. Answer in Chinese."));
        stepMap.put("product", createStep("product", "Handle product information questions. Answer in Chinese."));
        stepMap.put("general", createStep("general", "Handle general questions. Answer in Chinese."));
        return createRoutingWorkflow(stepMap);
    }

    public Workflow buildRagChain() {
        List<WorkflowStep> steps = List.of(
                createSearchStep("knowledge-search"),
                createContextAnswerStep("rag-answer", "你是一个知识库助手。根据提供的检索上下文回答用户问题。如果上下文中没有相关信息，请诚实回答不知道。回答使用中文。")
        );
        return createChainWorkflow(steps);
    }

    public Workflow buildRagRouting() {
        Map<String, WorkflowStep> stepMap = new LinkedHashMap<>();

        List<WorkflowStep> knowledgeSteps = List.of(
                createSearchStep("knowledge-search"),
                createContextAnswerStep("knowledge-answer", "根据检索到的上下文回答知识库问题。回答使用中文。")
        );
        stepMap.put("knowledge", new ChainStep("knowledge-chain", knowledgeSteps));
        stepMap.put("chat", createStep("chat", "你是一个友好的对话助手。自然地回答用户问题。回答使用中文。"));
        stepMap.put("coding", createStep("coding", "你是一个编程专家。帮助用户解决编程问题。回答使用中文。"));

        return createRoutingWorkflow(stepMap);
    }

    @PostConstruct
    public void logInit() {
        log.info("AgentWorkflowConfig initialized with ChatClient: {} and RetrievalService",
                chatClient.getClass().getSimpleName());
    }
}
