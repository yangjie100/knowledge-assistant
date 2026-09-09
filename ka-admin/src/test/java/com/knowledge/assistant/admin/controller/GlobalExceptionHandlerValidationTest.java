package com.knowledge.assistant.admin.controller;

import com.knowledge.assistant.common.dto.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Review HIGH regression lock: with the real {@link GlobalExceptionHandler} registered
 * (as it is in production), a @Valid failure must surface as HTTP 400 with field-level
 * detail — NOT be swallowed by the Exception catch-all into 200 + generic code 500.
 * Standalone MockMvc WITHOUT the advice cannot see this; the advice must be attached.
 */
class GlobalExceptionHandlerValidationTest {

    @RestController
    static class ValidationController {
        @PostMapping("/validate")
        public Result<Void> validate(@Valid @RequestBody SampleRequest request) {
            return Result.ok(null);
        }
    }

    static class SampleRequest {
        @NotBlank(message = "question must not be blank")
        private String question;

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ValidationController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validationFailureReturns400WithFieldDetail() throws Exception {
        mockMvc.perform(post("/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("question")));
    }

    @Test
    void validBodyStillPassesThroughAdvice() throws Exception {
        mockMvc.perform(post("/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
