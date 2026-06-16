package com.knowledge.assistant.chat.advisor;

import com.knowledge.assistant.rag.service.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Modular-RAG advisor: runs the hybrid retrieval pipeline (vector + BM25 RRF + TEI rerank)
 * inside the ChatClient advisor chain, instead of ChatService orchestrating it manually.
 *
 * <p>Spring AI 1.1.4 ships no {@code RetrievalAugmentationAdvisor} / {@code QuestionAnswerAdvisor}
 * (the {@code rag} and {@code advisors-vector-store} modules were dropped after the 1.0 split),
 * so this is a thin {@link BaseAdvisor} implementing only {@link #before} (retrieval + context
 * injection). The default {@code adviseCall}/{@code adviseStream} wire {@code before -> chain -> after},
 * so the call and stream paths are both covered. {@link #after} is a passthrough.
 *
 * <p>Registered per-call by {@code ChatService} (not as a default advisor) because the shared
 * {@code chatClient} bean is also used by the agent workflow, where RAG context injection would
 * corrupt routing / ReAct reasoning.
 *
 * <p>Ordering: {@link Ordered#HIGHEST_PRECEDENCE} runs before {@code MessageChatMemoryAdvisor}
 * (DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER = -2147482648), so memory captures the context-augmented
 * user message — identical to the prior manual-RAG behavior.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagAdvisor implements BaseAdvisor {

    private static final String USER_TEMPLATE =
            "Context:\n%s\n\nQuestion: %s\n\nPlease cite sources in your answer.";

    private final RetrievalService retrievalService;

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        String question = request.prompt().getUserMessage().getText();
        List<Document> docs = retrievalService.retrieve(question);
        String contextText = formatContext(docs);
        String augmented = String.format(USER_TEMPLATE, contextText, question);
        Prompt augmentedPrompt = request.prompt().augmentUserMessage(um -> new UserMessage(augmented));
        return request.mutate().prompt(augmentedPrompt).build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    @Override
    public String getName() {
        return "rag-advisor";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    // Mirrors the prior ChatService.formatContext: surfaces docId + rrfScore so the model can cite.
    private String formatContext(List<Document> docs) {
        return docs.stream()
                .map(doc -> {
                    String docId = doc.getMetadata().get("docId") instanceof String s ? s : "unknown";
                    double score = doc.getMetadata().get("rrfScore") instanceof Number n ? n.doubleValue() : 0.0;
                    return "[Source: " + docId + " (score: " + String.format("%.4f", score) + ")]\n" + doc.getText();
                })
                .collect(Collectors.joining("\n\n"));
    }
}
