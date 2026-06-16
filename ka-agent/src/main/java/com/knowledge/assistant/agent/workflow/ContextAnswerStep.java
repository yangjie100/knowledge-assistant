package com.knowledge.assistant.agent.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

/**
 * LLM step that answers from context already supplied in its input. It does NOT perform retrieval —
 * a preceding KnowledgeSearchStep (in the chain) is expected to have injected the retrieved documents
 * into the input string. The default system prompt is tuned for grounded knowledge-base answering.
 */
@Slf4j
public class ContextAnswerStep implements WorkflowStep {

    private static final String RAG_SYSTEM_PROMPT = """
            你是一个知识库助手。请严格根据提供的上下文文档回答用户问题。
            如果上下文中没有相关信息，请诚实回答不知道。
            回答使用中文。""";

    private final ChatClient chatClient;
    private final String stepName;
    private final String systemPrompt;

    public ContextAnswerStep(ChatClient chatClient, String stepName) {
        this(chatClient, stepName, RAG_SYSTEM_PROMPT);
    }

    public ContextAnswerStep(ChatClient chatClient, String stepName, String systemPrompt) {
        this.chatClient = chatClient;
        this.stepName = stepName;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String name() {
        return stepName;
    }

    @Override
    public String execute(String input) {
        log.info("ContextAnswerStep '{}' processing input", stepName);
        return chatClient.prompt()
                .system(systemPrompt)
                .user(input)
                .call()
                .content();
    }
}
