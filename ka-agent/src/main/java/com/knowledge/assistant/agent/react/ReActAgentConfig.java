package com.knowledge.assistant.agent.react;

import com.knowledge.assistant.agent.tools.CalculatorTool;
import com.knowledge.assistant.agent.tools.DateTimeTool;
import com.knowledge.assistant.agent.tools.KnowledgeSearchTool;
import com.knowledge.assistant.agent.tools.KnowledgeStatsTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class ReActAgentConfig {

    private static final String SYSTEM_PROMPT = """
            你是一个智能助手，可以调用工具来辅助回答问题。
            可用工具: searchKnowledge(搜索知识库), getCurrentDateTime(当前时间), getStats(系统状态), calculate(计算器)。
            回答使用中文。先思考需要哪些工具，再调用，最后综合结果回答。
            如果不需要工具就能回答，直接回答即可。""";

    @Bean
    @Qualifier("reactChatClient")
    public ChatClient reactChatClient(ChatModel chatModel,
                                       KnowledgeSearchTool searchTool,
                                       DateTimeTool dateTimeTool,
                                       KnowledgeStatsTool statsTool,
                                       CalculatorTool calcTool) {
        // Convert @Tool annotated objects to ToolCallback[]
        ToolCallback[] toolCallbacks = ToolCallbacks.from(searchTool, dateTimeTool, statsTool, calcTool);

        // Build ToolCallingManager with static resolver
        ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
                .toolCallbackResolver(new StaticToolCallbackResolver(Arrays.asList(toolCallbacks)))
                .build();

        // Build ToolCallAdvisor
        ToolCallAdvisor toolCallAdvisor = ToolCallAdvisor.builder()
                .toolCallingManager(toolCallingManager)
                .build();

        // Build ChatClient with system prompt and tool call advisor
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(toolCallAdvisor)
                .build();
    }

    @Bean
    public ReActAgent reActAgent(@Qualifier("reactChatClient") ChatClient reactChatClient) {
        return new ReActAgent(reactChatClient);
    }
}
