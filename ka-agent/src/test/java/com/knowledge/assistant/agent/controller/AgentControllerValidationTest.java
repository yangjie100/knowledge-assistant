package com.knowledge.assistant.agent.controller;

import com.knowledge.assistant.agent.config.AgentWorkflowConfig;
import com.knowledge.assistant.agent.react.AgentExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M-2: @Valid must fire on agent endpoints — blank/null question previously fell
 * through to a 500 from NPE instead of a clean 400. Standalone MockMvc, no context.
 */
@ExtendWith(MockitoExtension.class)
class AgentControllerValidationTest {

    @Mock private AgentWorkflowConfig agentWorkflowConfig;
    @Mock private AgentExecutor agentExecutor;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AgentController(agentWorkflowConfig, agentExecutor)).build();
    }

    @Test
    void reactRejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/agent/react")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chainRejectsMissingQuestion() throws Exception {
        mockMvc.perform(post("/api/agent/chain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
