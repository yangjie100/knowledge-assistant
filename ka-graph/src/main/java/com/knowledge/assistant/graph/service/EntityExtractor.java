package com.knowledge.assistant.graph.service;

import com.knowledge.assistant.graph.model.ExtractionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Extracts entities and relations from a text chunk via GLM-5.2 structured output.
 *
 * <p>Builds a fresh, advisor-free ChatClient per call — the shared chatClient bean carries
 * memory/rerank advisors that would pollute a stateless extraction (same reasoning as
 * WorkflowChatClientConfig). Extraction failure degrades to empty results rather than
 * aborting the graph-build pipeline.
 *
 * <p>Conditional on ka.graph.enabled so the whole module is inert without Neo4j.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ka.graph.enabled", havingValue = "true")
public class EntityExtractor {

    private static final String SYSTEM_PROMPT = """
            你是知识图谱实体与关系抽取专家。从用户提供的文本片段中抽取关键实体及实体间关系。

            抽取规则:
            1. 实体类型示例:技术、组件、框架、模块、方法、概念、工具、配置项。
            2. 关系类型示例:依赖、包含、实现、调用、继承、配置、属于、替代。
            3. 只抽取文本中明确出现的实体与关系,严禁编造或脑补。
            4. 中文文本抽取中文实体名,专有名词保留原文(如 Redis、Spring AI)。
            5. 实体 name 必须简短规范(2-10 字),description 用一句话说明。
            6. 若文本无清晰实体/关系,返回空列表,不要硬凑。
            """;

    private final ChatModel chatModel;

    /**
     * GLM (ZhiPuAi) ChatModel by qualifier — the app keeps Ollama + ZhiPuAi chat models
     * side-by-side (see application.yml), so a bare ChatModel injection is ambiguous
     * (NoUniqueBeanDefinition, two candidates). Hand-written ctor since
     * @RequiredArgsConstructor drops @Qualifier (same reason AgentWorkflowConfig writes its own).
     */
    public EntityExtractor(@Qualifier("zhiPuAiChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public ExtractionResult extract(String text) {
        try {
            ChatClient client = ChatClient.builder(chatModel)
                    .defaultSystem(SYSTEM_PROMPT)
                    .build();
            return client.prompt()
                    .user(text)
                    .call()
                    .entity(ExtractionResult.class);
        } catch (Exception e) {
            log.warn("Entity extraction failed (returning empty): {}", e.getMessage());
            return new ExtractionResult(List.of(), List.of());
        }
    }
}
