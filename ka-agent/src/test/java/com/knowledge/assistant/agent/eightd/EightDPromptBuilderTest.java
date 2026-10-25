package com.knowledge.assistant.agent.eightd;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for prompt assembly — no Spring context, no LLM, no Redis.
 */
class EightDPromptBuilderTest {

    private final EightDPromptBuilder builder = new EightDPromptBuilder();

    private static final String JSON_RECORD = """
            {"recordId":"QR-2026-1001","productCode":"ZK-A5 接口板组件","batchNo":"P2026-1018-02",
             "process":"回流焊后 AOI + 电测","defectDescription":"AOI 检出 Chip 元件焊点异常，电测 3 块网络开路"}""";

    @Test
    void retrievalQueryExtractsStructuredFieldsFromJsonRecord() {
        String query = builder.buildRetrievalQuery(JSON_RECORD);
        assertThat(query)
                .contains("ZK-A5 接口板组件")
                .contains("回流焊后 AOI + 电测")
                .contains("AOI 检出 Chip 元件焊点异常");
    }

    @Test
    void retrievalQueryFallsBackToRawTextForNonJsonRecord() {
        String query = builder.buildRetrievalQuery("波峰焊后电测发现网络开路，切片确认虚焊，批次暂停流转待分析。");
        assertThat(query).contains("波峰焊后电测");
    }

    @Test
    void retrievalQueryIsCappedAtMaxLength() {
        String query = builder.buildRetrievalQuery("x".repeat(1000));
        assertThat(query).hasSizeLessThanOrEqualTo(EightDPromptBuilder.MAX_QUERY_CHARS);
    }

    @Test
    void retrievalQueryHandlesBlankInput() {
        assertThat(builder.buildRetrievalQuery("  ")).isEmpty();
    }

    @Test
    void userPromptContainsFullEightDStructureRequirements() {
        String prompt = builder.buildUserPrompt(JSON_RECORD, List.of());
        assertThat(prompt)
                .contains("D1")
                .contains("D8")
                .contains("5Why")
                .contains("直接原因/流出原因/系统原因")
                .contains("（待确认）");
    }

    @Test
    void userPromptEmbedsRetrievedCases() {
        Document doc = Document.builder().text("8D-2026-011 波峰焊虚焊案例正文").build();
        String prompt = builder.buildUserPrompt(JSON_RECORD, List.of(doc));
        assertThat(prompt).contains("案例[1]").contains("波峰焊虚焊案例正文");
    }

    @Test
    void userPromptHandlesNoCasesExplicitly() {
        String prompt = builder.buildUserPrompt(JSON_RECORD, List.of());
        assertThat(prompt).contains("未检索到相似案例");
    }

    @Test
    void userPromptTruncatesOverlongCaseText() {
        String head = "HEAD_MARKER";
        String filler = IntStream.range(0, 3000).mapToObj(i -> "字").collect(Collectors.joining());
        Document doc = Document.builder().text(head + filler + "TAIL_MARKER").build();
        String prompt = builder.buildUserPrompt(JSON_RECORD, List.of(doc));
        assertThat(prompt).contains(head).doesNotContain("TAIL_MARKER").contains("已截断");
    }
}
