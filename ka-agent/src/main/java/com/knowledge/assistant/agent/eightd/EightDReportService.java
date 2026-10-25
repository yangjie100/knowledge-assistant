package com.knowledge.assistant.agent.eightd;

import com.knowledge.assistant.rag.service.RetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates 8D report generation: RAG retrieval of similar historical cases
 * followed by single-shot LLM generation with a disciplined prompt. Runs on the
 * conditional dual ChatClient (GLM dev / Ollama offline demo).
 */
@Service
@Slf4j
public class EightDReportService {

    /** Log-discipline cap, aligned with KnowledgeSearchStep: business-sensitive record text. */
    private static final int LOG_QUERY_PREFIX_CHARS = 50;

    private final RetrievalService retrievalService;
    private final EightDPromptBuilder promptBuilder;
    private final ChatClient eightDChatClient;

    // Hand-written constructor + @Qualifier: multiple ChatClient beans coexist
    // (chatClient/@Primary, glmChatClient, reactChatClient, eightDChatClient).
    // @RequiredArgsConstructor would silently resolve the @Primary chatClient.
    public EightDReportService(RetrievalService retrievalService,
                               EightDPromptBuilder promptBuilder,
                               @Qualifier("eightDChatClient") ChatClient eightDChatClient) {
        this.retrievalService = retrievalService;
        this.promptBuilder = promptBuilder;
        this.eightDChatClient = eightDChatClient;
    }

    public String generateReport(String qualityRecord) {
        String query = promptBuilder.buildRetrievalQuery(qualityRecord);
        List<Document> similarCases = retrievalService.retrieve(query);
        log.info("8D generation: retrieval query length: {}, prefix: '{}', similar cases: {}",
                query.length(),
                query.substring(0, Math.min(query.length(), LOG_QUERY_PREFIX_CHARS)),
                similarCases.size());
        String userPrompt = promptBuilder.buildUserPrompt(qualityRecord, similarCases);
        String report = eightDChatClient.prompt().user(userPrompt).call().content();
        if (report == null || report.isBlank()) {
            log.warn("8D generation returned empty content, similar cases: {}", similarCases.size());
            return "";
        }
        return report.strip();
    }
}
