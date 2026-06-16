package com.knowledge.assistant.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Workflow-dedicated ChatClient, mirroring the P0-3 reactChatClient dual-bean pattern.
 *
 * <p>Defined in its own @Configuration (NOT inside {@link AgentWorkflowConfig}) to avoid a
 * self-reference cycle: {@code AgentWorkflowConfig}'s constructor injects workflowChatClient,
 * so if the bean factory method lived in the same class, creating the @Configuration instance
 * would require the very bean it itself produces — BeanCurrentlyInCreationException.
 *
 * <p>GLM-5.2 preferred (~15s vs deepseek-r1's ~180s for long Chinese answers), Ollama
 * deepseek-r1 fallback when ZHIPU_API_KEY is absent. Mutually-exclusive
 * @ConditionalOnExpression + same bean name => exactly one registers.
 *
 * <p>Deliberately NO memory advisor and NO defaultTools: workflow steps (ChatClientStep /
 * ContextAnswerStep / RouterSelectorStep) are stateless single-shot prompts. The
 * MessageChatMemoryAdvisor on the @Primary chatClient would leak conversation state across
 * requests and pollute routing decisions. Each step supplies its own systemPrompt.
 */
@Configuration
public class WorkflowChatClientConfig {

    @Bean("workflowChatClient")
    @ConditionalOnExpression("'${spring.ai.zhipuai.api-key:}' != ''")
    public ChatClient workflowChatClientGlm(ZhiPuAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean("workflowChatClient")
    @ConditionalOnExpression("'${spring.ai.zhipuai.api-key:}' == ''")
    public ChatClient workflowChatClientOllama(OllamaChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
