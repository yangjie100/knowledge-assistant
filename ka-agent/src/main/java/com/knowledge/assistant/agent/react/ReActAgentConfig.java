package com.knowledge.assistant.agent.react;

import com.knowledge.assistant.agent.tools.CalculatorTool;
import com.knowledge.assistant.agent.tools.DateTimeTool;
import com.knowledge.assistant.agent.tools.KnowledgeSearchTool;
import com.knowledge.assistant.agent.tools.KnowledgeStatsTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReActAgentConfig {

    private static final String SYSTEM_PROMPT = """
            你是一个智能助手，可以调用工具来辅助回答问题。
            可用工具: searchKnowledge(搜索知识库), getCurrentDateTime(当前时间), getStats(系统状态), calculate(计算器)。
            回答使用中文。先思考需要哪些工具，再调用，最后综合结果回答。
            如果不需要工具就能回答，直接回答即可。""";

    /**
     * P0-3b: reactChatClient uses Spring AI 1.1.x idiomatic tool mounting — defaultTools(@Tool beans)
     * lets the framework auto-register ToolCallbacks AND enable internal tool execution (the
     * Thought→Action→Observation loop runs inside ChatModel.call). Replaces the prior hand-built
     * ToolCallAdvisor + DefaultToolCallingManager + StaticToolCallbackResolver, which failed to
     * execute tools (GLM's tool_calls came back as <tool_call> text because the execution loop
     * was never engaged). defaultTools(Object...) accepts @Tool-annotated beans directly.
     *
     * Conditional dual-bean: GLM preferred (stable native function calling), Ollama fallback
     * when ZHIPU_API_KEY absent. Same bean name + mutually-exclusive @ConditionalOnExpression
     * => exactly one registers; reActAgent(@Qualifier("reactChatClient")) always resolves.
     */
    @Bean("reactChatClient")
    @ConditionalOnExpression("'${spring.ai.zhipuai.api-key:}' != ''")
    public ChatClient reactChatClientGlm(ZhiPuAiChatModel chatModel,
                                          KnowledgeSearchTool searchTool,
                                          DateTimeTool dateTimeTool,
                                          KnowledgeStatsTool statsTool,
                                          CalculatorTool calcTool) {
        // GLM (ZhiPuAiChatModel) — native function calling, stable tool invocation
        return buildReactClient(chatModel, searchTool, dateTimeTool, statsTool, calcTool);
    }

    @Bean("reactChatClient")
    @ConditionalOnExpression("'${spring.ai.zhipuai.api-key:}' == ''")
    public ChatClient reactChatClientOllama(OllamaChatModel chatModel,
                                             KnowledgeSearchTool searchTool,
                                             DateTimeTool dateTimeTool,
                                             KnowledgeStatsTool statsTool,
                                             CalculatorTool calcTool) {
        // Ollama (deepseek-r1) fallback — ZHIPU_API_KEY not set, keeps app bootable
        return buildReactClient(chatModel, searchTool, dateTimeTool, statsTool, calcTool);
    }

    private ChatClient buildReactClient(ChatModel chatModel,
                                         KnowledgeSearchTool searchTool,
                                         DateTimeTool dateTimeTool,
                                         KnowledgeStatsTool statsTool,
                                         CalculatorTool calcTool) {
        // defaultTools(@Tool beans): framework auto-registers ToolCallbacks and enables
        // internal tool execution — no manual ToolCallAdvisor/ToolCallingManager needed.
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(searchTool, dateTimeTool, statsTool, calcTool)
                .build();
    }

    @Bean
    public ReActAgent reActAgent(@Qualifier("reactChatClient") ChatClient reactChatClient) {
        return new ReActAgent(reactChatClient);
    }
}

