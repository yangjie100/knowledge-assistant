package com.knowledge.assistant.agent.workflow;

import org.springframework.ai.chat.client.ChatClient;

/**
 * ChatClient-based workflow step implementation
 */
public class ChatClientStep implements WorkflowStep {

    private final ChatClient chatClient;
    private final String stepName;
    private final String systemPrompt;

    public ChatClientStep(ChatClient chatClient, String stepName, String systemPrompt) {
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
        return chatClient.prompt()
                .system(systemPrompt)
                .user(input)
                .call()
                .content();
    }
}
