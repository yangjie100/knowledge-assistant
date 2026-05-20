package com.knowledge.assistant.agent.react;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ReActAgent {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReActAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public ReActResponse execute(ReActRequest request) {
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

            return ReActResponse.builder()
                    .content(answer)
                    .steps(steps)
                    .success(true)
                    .build();
        } catch (Exception e) {
            log.error("ReAct failed", e);
            return ReActResponse.builder()
                    .success(false)
                    .errorMessage("Agent failed: " + e.getMessage())
                    .steps(steps)
                    .build();
        }
    }

    public Flux<String> streamExecute(ReActRequest request) {
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

    private void validate(ReActRequest r) {
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
