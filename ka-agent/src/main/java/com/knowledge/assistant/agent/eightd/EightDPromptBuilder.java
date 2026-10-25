package com.knowledge.assistant.agent.eightd;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Assembles retrieval query and generation prompt for 8D report generation.
 * Pure logic without infra dependencies — unit-testable without Spring context.
 */
@Component
public class EightDPromptBuilder {

    static final int MAX_QUERY_CHARS = 400;
    static final int MAX_RECORD_CHARS = 4000;
    static final int MAX_CASE_CHARS = 2500;

    /** Structured fields of a MES quality record used as retrieval keywords. */
    private static final Pattern RECORD_FIELD =
            Pattern.compile("\"(productCode|process|defectDescription)\"\\s*:\\s*\"([^\"]*)\"");

    private static final String REQUIREMENTS = """
            【撰写要求】
            1. 输出语言：中文；输出格式：Markdown。直接输出报告正文，不要输出"好的""以下是"等任何多余文字。
            2. 报告结构严格为：
               - 首部信息表：报告编号、开具日期、产品代号、批次号、异常等级、状态
               - D1 团队组建：角色/部门/职责分工表
               - D2 问题描述：5W2H（What/When/Where/Who/Which/How detected/How many）
               - D3 临时围堵措施：序号/措施/完成时间/状态表
               - D4 根本原因分析：4.1 鱼骨图 5M1E 排查表（含"排查结论"）；4.2 5Why 分析链；4.3 直接原因/流出原因/系统原因判定表
               - D5 永久纠正措施：序号/针对原因/措施/责任/期限表
               - D6 实施与效果验证：验证方法、验证数据、判定
               - D7 预防再发与体系固化：固化措施/落实证据表
               - D8 结案与团队认可
            3. 分析纪律：
               - 记录中已有的数据（批次号、数量、检验数据、时间）必须原样引用，不得改动或编造；
               - 记录未给出的信息（根因结论、责任人、日期等）按工程惯例给出合理初判，并在该条目末尾标注"（待确认）"；
               - D4 必须区分"直接原因/流出原因/系统原因"三个层次；
               - 历史案例与本案异常模式相似时借鉴其分析路径而非结论；与本案数据冲突时以本案为准。""";

    public String buildRetrievalQuery(String qualityRecord) {
        if (qualityRecord == null || qualityRecord.isBlank()) {
            return "";
        }
        Matcher matcher = RECORD_FIELD.matcher(qualityRecord);
        String query = matcher.results()
                .map(m -> m.group(2))
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(" "));
        if (query.isBlank()) {
            query = qualityRecord.strip();
        }
        return query.length() > MAX_QUERY_CHARS ? query.substring(0, MAX_QUERY_CHARS) : query;
    }

    public String buildUserPrompt(String qualityRecord, List<Document> similarCases) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请基于以下质量异常记录，撰写一份完整的 8D 质量异常分析报告初稿。\n\n");
        prompt.append("【质量异常记录】\n").append(truncate(qualityRecord, MAX_RECORD_CHARS)).append("\n\n");
        prompt.append("【知识库检索到的历史相似案例】\n");
        if (similarCases == null || similarCases.isEmpty()) {
            prompt.append("（未检索到相似案例，请按 8D 方法论独立分析。）\n\n");
        } else {
            prompt.append("（以下案例仅作分析框架与深度参照，其结论与本案无关，禁止照抄。）\n");
            for (int i = 0; i < similarCases.size(); i++) {
                prompt.append("---\n案例[").append(i + 1).append("]:\n")
                        .append(truncate(similarCases.get(i).getText(), MAX_CASE_CHARS)).append("\n");
            }
            prompt.append("\n");
        }
        return prompt.append(REQUIREMENTS).toString();
    }

    private String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        return text.length() > maxChars ? text.substring(0, maxChars) + "\n…（已截断）" : text;
    }
}
