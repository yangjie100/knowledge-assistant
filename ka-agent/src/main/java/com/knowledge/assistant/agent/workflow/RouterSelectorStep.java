package com.knowledge.assistant.agent.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI router selector: uses ChatClient to classify input and return a route key
 */
@Slf4j
public class RouterSelectorStep implements WorkflowStep {

    private static final String ROUTER_PROMPT = """
            You are a professional router selector. Based on the user question, select the most appropriate route from the options below:

            Available routes:
            %s

            Return ONLY the route key name, with no extra explanation. For example, if the best route is "technical", return just "technical".""";

    private final ChatClient chatClient;
    private final Map<String, WorkflowStep> stepMap;
    private final String stepName;

    public RouterSelectorStep(ChatClient chatClient, Map<String, WorkflowStep> stepMap, String stepName) {
        this.chatClient = chatClient;
        this.stepMap = stepMap;
        this.stepName = stepName;
    }

    @Override
    public String name() {
        return stepName;
    }

    @Override
    public String execute(String input) {
        String routeInfo = stepMap.entrySet().stream()
                .map(entry -> "- " + entry.getKey() + ": " + entry.getValue().name())
                .collect(Collectors.joining("\n"));

        String systemPrompt = String.format(ROUTER_PROMPT, routeInfo);

        String routeKey = chatClient.prompt()
                .system(systemPrompt)
                .user(input)
                .call()
                .content();

        if (routeKey != null && stepMap.containsKey(routeKey.trim())) {
            return routeKey.trim();
        }

        log.warn("Router returned invalid key: {}", routeKey);
        return null;
    }
}
