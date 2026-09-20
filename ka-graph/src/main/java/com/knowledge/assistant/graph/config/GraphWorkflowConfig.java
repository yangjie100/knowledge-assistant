package com.knowledge.assistant.graph.config;

import com.knowledge.assistant.agent.workflow.ContextAnswerStep;
import com.knowledge.assistant.agent.workflow.Workflow;
import com.knowledge.assistant.agent.workflow.WorkflowStep;
import com.knowledge.assistant.agent.workflow.impl.ChainWorkflow;
import com.knowledge.assistant.graph.service.GraphRetrievalService;
import com.knowledge.assistant.graph.workflow.GraphRetrievalStep;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Assembles the GraphRAG chain: GraphRetrievalStep (3-step retrieval -> context) followed by
 * ContextAnswerStep (answer from context). Conditional on ka.graph.enabled: when off, the
 * GraphRetrievalService bean is absent, so this config must be absent too to avoid a
 * missing-bean failure. Hand-written constructor mirrors AgentWorkflowConfig so the
 * @Qualifier("workflowChatClient") survives (Lombok does not copy @Qualifier onto generated
 * constructor params without a lombok.config copyableAnnotations entry).
 */
@Configuration
@ConditionalOnProperty(name = "ka.graph.enabled", havingValue = "true")
public class GraphWorkflowConfig {

    private static final String GRAPH_ANSWER_PROMPT = """
            你是一个基于知识图谱的知识库助手。请综合【相关文档片段】与【相关实体与关系】回答用户问题。
            优先利用实体间的关系进行多跳推理；若信息不足，请诚实回答不知道。
            回答使用中文。""";

    private final ChatClient chatClient;
    private final GraphRetrievalService graphRetrievalService;

    public GraphWorkflowConfig(@Qualifier("workflowChatClient") ChatClient chatClient,
                               GraphRetrievalService graphRetrievalService) {
        this.chatClient = chatClient;
        this.graphRetrievalService = graphRetrievalService;
    }

    public Workflow buildGraphRagChain() {
        List<WorkflowStep> steps = List.of(
                new GraphRetrievalStep(graphRetrievalService, "graph-search"),
                new ContextAnswerStep(chatClient, "graph-answer", GRAPH_ANSWER_PROMPT)
        );
        return new ChainWorkflow(steps);
    }
}
