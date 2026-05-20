package com.knowledge.assistant.agent.react;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentStep {
    private String phase;
    private String tool;
    private String input;
    private String output;
    private String content;
    private long timestamp;
}
