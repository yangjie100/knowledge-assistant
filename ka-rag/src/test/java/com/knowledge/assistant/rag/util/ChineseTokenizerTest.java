package com.knowledge.assistant.rag.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChineseTokenizerTest {

    private final ChineseTokenizer tokenizer = new ChineseTokenizer();

    @Test
    void segmentsChineseQuestionIntoOrExpression() {
        // q06 raw sentence: 整句传 FT.SEARCH = 0 命中(根因 A). 分词后应保留 对话/记忆 实词.
        String result = tokenizer.toRediSearchQuery("项目的对话记忆是如何实现的？");
        assertThat(result).startsWith("(").endsWith(")");
        assertThat(result).contains("对话");
        assertThat(result).contains("记忆");
        assertThat(result).doesNotContain("如何");
        assertThat(result).doesNotContain("？");
    }

    @Test
    void keepsChineseAndEnglishKeywordsTogether() {
        // q02 raw sentence: 0 命中. 分词后应保留 组件/LLM, 去问句词 哪个.
        // jieba lowercases english tokens (LLM->llm); RediSearch TEXT is case-insensitive so recall is unaffected.
        String result = tokenizer.toRediSearchQuery("用哪个组件提供 LLM 推理能力？");
        assertThat(result).contains("组件");
        assertThat(result).containsIgnoringCase("LLM");
        assertThat(result).doesNotContain("哪个");
    }

    @Test
    void returnsNullWhenNoUsableKeyword() {
        assertThat(tokenizer.toRediSearchQuery(null)).isNull();
        assertThat(tokenizer.toRediSearchQuery("")).isNull();
        assertThat(tokenizer.toRediSearchQuery("   ")).isNull();
        assertThat(tokenizer.toRediSearchQuery("的吗呢吧")).isNull();
    }

    @Test
    void keepsExpressionBalancedAndFiltersSpecialChars() {
        // RediSearch syntax chars in input must not break the OR expression structure.
        String result = tokenizer.toRediSearchQuery("Redis 向量存储(vector)");
        assertThat(result).startsWith("(").endsWith(")");
        long open = result.chars().filter(c -> c == '(').count();
        long close = result.chars().filter(c -> c == ')').count();
        assertThat(open).isEqualTo(close);
    }

    @Test
    void deduplicatesKeywords() {
        String result = tokenizer.toRediSearchQuery("对话 对话 记忆");
        assertThat(result).isNotNull();
        // deduplicated -> first occurrence equals last occurrence of 对话
        assertThat(result.indexOf("对话")).isEqualTo(result.lastIndexOf("对话"));
    }
}
