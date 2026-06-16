package com.knowledge.assistant.agent.react;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin facade over a tool-augmented ChatClient (the {@code reactChatClient} bean). The actual
 * ReAct (Thought→Action→Observation) loop is NOT hand-rolled here — it runs inside Spring AI's
 * ChatModel, enabled by {@code defaultTools(@Tool)} on the client (see AgentExecutorConfig). This
 * class only drives a single call and wraps its output into reasoning/answer phases for both the
 * blocking and the SSE (streaming) response paths.
 */
@Slf4j
public class AgentExecutor {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentExecutor(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public AgentResponse execute(AgentRequest request) {
        validate(request);
        List<AgentStep> steps = new ArrayList<>();
        try {
            log.info("ReAct processing: {}", request.question());
            steps.add(step("reasoning", "Processing: " + request.question()));

            String answer = chatClient.prompt()
                    .user(request.question())
                    .call()
                    .content();

            steps.add(step("answer", answer));

            return AgentResponse.builder()
                    .content(answer)
                    .steps(steps)
                    .success(true)
                    .build();
        } catch (Exception e) {
            log.error("ReAct failed", e);
            return AgentResponse.builder()
                    .success(false)
                    .errorMessage("Agent failed: " + e.getMessage())
                    .steps(steps)
                    .build();
        }
    }

    public Flux<String> streamExecute(AgentRequest request) {
        validate(request);
        return Flux.create(sink -> {
            try {
                sink.next(sse("thinking", step("reasoning", "Processing: " + request.question())));
                chatClient.prompt()
                        .user(request.question())
                        .stream()
                        .content()
                        .doOnNext(chunk -> sink.next(sse("answer", step("answer", chunk))))
                        .doOnComplete(sink::complete)
                        .doOnError(sink::error)
                        .subscribe();
            } catch (Exception e) {
                sink.next(sse("error", step("error", e.getMessage())));
                sink.complete();
            }
        });
    }

    private void validate(AgentRequest r) {
        if (r.question() == null || r.question().isBlank()) {
            throw new IllegalArgumentException("Question must not be blank");
        }
    }

    private AgentStep step(String phase, String content) {
        return AgentStep.builder()
                .phase(phase)
                .content(content)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    private String sse(String event, AgentStep step) {
        try {
            return "event: " + event + "\ndata: " + objectMapper.writeValueAsString(step) + "\n\n";
        } catch (JsonProcessingException e) {
            return "event: error\ndata: {\"phase\":\"error\"}\n\n";
        }
    }
}
