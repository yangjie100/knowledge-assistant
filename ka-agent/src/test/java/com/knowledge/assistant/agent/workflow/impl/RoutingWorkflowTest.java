package com.knowledge.assistant.agent.workflow.impl;

import com.knowledge.assistant.agent.workflow.WorkflowRequest;
import com.knowledge.assistant.agent.workflow.WorkflowResponse;
import com.knowledge.assistant.agent.workflow.WorkflowStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RoutingWorkflowTest {

    @Test
    @DisplayName("Routing workflow should route to correct step based on router result")
    void shouldRouteToCorrectStep() {
        // Given
        WorkflowStep router = new ChainWorkflowTest.StubStep("router", s -> "technical");
        Map<String, WorkflowStep> stepMap = new LinkedHashMap<>();
        stepMap.put("billing", new ChainWorkflowTest.StubStep("billing", s -> "Billing response: " + s));
        stepMap.put("technical", new ChainWorkflowTest.StubStep("technical", s -> "Technical response: " + s));
        stepMap.put("general", new ChainWorkflowTest.StubStep("general", s -> "General response: " + s));

        RoutingWorkflow workflow = new RoutingWorkflow(router, stepMap);

        // When
        WorkflowResponse response = workflow.execute(new WorkflowRequest("My computer won't start"));

        // Then
        assertTrue(response.isSuccess());
        assertEquals("Technical response: My computer won't start", response.getContent());
    }

    @Test
    @DisplayName("Routing workflow should return error when router returns unknown key")
    void shouldReturnErrorForUnknownRoute() {
        // Given
        WorkflowStep router = new ChainWorkflowTest.StubStep("router", s -> "unknown_route");
        Map<String, WorkflowStep> stepMap = new LinkedHashMap<>();
        stepMap.put("billing", new ChainWorkflowTest.StubStep("billing", s -> "billing"));

        RoutingWorkflow workflow = new RoutingWorkflow(router, stepMap);

        // When
        WorkflowResponse response = workflow.execute(new WorkflowRequest("test"));

        // Then
        assertFalse(response.isSuccess());
        assertTrue(response.getErrorMessage().contains("unknown_route"));
    }

    @Test
    @DisplayName("Routing workflow should handle different route selections")
    void shouldHandleDifferentRoutes() {
        // Given
        WorkflowStep router = new ChainWorkflowTest.StubStep("router", s -> {
            if (s.contains("bill")) return "billing";
            if (s.contains("error")) return "technical";
            return "general";
        });

        Map<String, WorkflowStep> stepMap = new LinkedHashMap<>();
        stepMap.put("billing", new ChainWorkflowTest.StubStep("billing", s -> "BILL"));
        stepMap.put("technical", new ChainWorkflowTest.StubStep("technical", s -> "TECH"));
        stepMap.put("general", new ChainWorkflowTest.StubStep("general", s -> "GEN"));

        RoutingWorkflow workflow = new RoutingWorkflow(router, stepMap);

        // When & Then
        WorkflowResponse r1 = workflow.execute(new WorkflowRequest("I have a bill question"));
        assertTrue(r1.isSuccess());
        assertEquals("BILL", r1.getContent());

        WorkflowResponse r2 = workflow.execute(new WorkflowRequest("I have an error"));
        assertTrue(r2.isSuccess());
        assertEquals("TECH", r2.getContent());

        WorkflowResponse r3 = workflow.execute(new WorkflowRequest("Hello world"));
        assertTrue(r3.isSuccess());
        assertEquals("GEN", r3.getContent());
    }
}
