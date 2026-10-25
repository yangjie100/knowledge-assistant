package com.knowledge.assistant.agent.eightd;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient dedicated to 8D report generation: single-shot generation, no tools,
 * no agent loop. Mirrors AgentExecutorConfig's dual-bean pattern — GLM (cloud,
 * fast dev iteration) when api-key present, Ollama (local qwen3.8:27b, offline
 * demo "unplug-the-network" mode) otherwise.
 */
@Configuration
public class EightDConfig {

    private static final String SYSTEM_PROMPT = """
            你是一名军工电子制造行业的资深质量工程师，精通 8D 问题解决方法论、5Why 与鱼骨图（5M1E）分析。\
            你撰写的质量报告结构严谨、表述规范、结论有据，符合军工质量文档的行文风格。你只输出报告本体。""";

    @Bean("eightDChatClient")
    @ConditionalOnExpression("'${spring.ai.zhipuai.api-key:}' != ''")
    public ChatClient eightDChatClientGlm(ZhiPuAiChatModel chatModel) {
        return build(chatModel);
    }

    @Bean("eightDChatClient")
    @ConditionalOnExpression("'${spring.ai.zhipuai.api-key:}' == ''")
    public ChatClient eightDChatClientOllama(OllamaChatModel chatModel) {
        return build(chatModel);
    }

    private ChatClient build(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }
}
