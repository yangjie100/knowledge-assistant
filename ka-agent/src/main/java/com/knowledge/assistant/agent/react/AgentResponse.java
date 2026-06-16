package com.knowledge.assistant.agent.react;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AgentResponse {
    private String content;
    private List<AgentStep> steps;
    private boolean success;
    private String errorMessage;
}
