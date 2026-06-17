package com.knowledge.assistant.rag.util;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converts a natural-language query into a RediSearch OR expression so that
 * Chinese full-text (BM25) search works against RediSearch's whitespace tokenizer.
 *
 * <p>RediSearch splits on whitespace by default, so a raw Chinese sentence like
 * "项目的对话记忆是如何实现的？" becomes one unusable token and never matches. Jieba
 * segments the sentence (SEARCH mode), stop-words are dropped, and the remaining
 * keywords are joined with {@code |} inside parentheses, e.g. {@code (对话|记忆|实现)}.
 * RediSearch treats {@code |} as OR, so any one keyword surfacing a document is
 * enough - which is exactly what Chinese QA retrieval needs.
 */
@Component
public class ChineseTokenizer {

    /** Stop-words that carry no retrieval signal (function words, particles, pronouns). */
    private static final Set<String> STOPWORDS = Set.of(
            // particles / copula / adverbs
            "的", "了", "着", "过", "地", "得", "是", "也", "都", "就", "还", "又", "已",
            // interrogative / sentence particles
            "如何", "怎么", "怎样", "什么", "哪个", "哪些", "哪", "为何", "为什么", "吗", "呢", "吧", "啊", "呀",
            // pronouns
            "我", "你", "他", "她", "它", "我们", "你们", "他们", "这", "那", "这个", "那个", "其",
            // prepositions / conjunctions
            "在", "用", "和", "与", "或", "及", "把", "被", "让", "给", "对", "从", "向", "为",
            // measure words
            "个", "些"
    );

    /** Characters with special meaning in RediSearch query syntax - tokens containing them are dropped. */
    private static final String SPECIAL_CHARS = "()|:*\"'@[]{}~/^$<>=+";

    private final JiebaSegmenter segmenter = new JiebaSegmenter();

    /**
     * @param query raw natural-language query
     * @return RediSearch OR expression {@code (w1|w2|...)}; {@code null} if no usable keyword
     *         remains (caller should fall back to the raw query)
     */
    public String toRediSearchQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        List<SegToken> tokens = segmenter.process(query, JiebaSegmenter.SegMode.SEARCH);
        List<String> keywords = tokens.stream()
                .map(t -> t.word.trim())
                .filter(w -> !w.isEmpty())
                .filter(w -> !STOPWORDS.contains(w))
                .filter(ChineseTokenizer::hasLetterOrDigit)
                .filter(w -> !containsSpecial(w))
                .filter(w -> isCjk(w) || w.length() >= 2)
                .distinct()
                .collect(Collectors.toList());
        if (keywords.isEmpty()) {
            return null;
        }
        return "(" + String.join("|", keywords) + ")";
    }

    private static boolean isCjk(String w) {
        if (w.isEmpty()) return false;
        Character.UnicodeBlock b = Character.UnicodeBlock.of(w.charAt(0));
        return b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B;
    }

    private static boolean hasLetterOrDigit(String w) {
        return w.chars().anyMatch(Character::isLetterOrDigit);
    }

    private static boolean containsSpecial(String w) {
        return w.chars().anyMatch(c -> SPECIAL_CHARS.indexOf(c) >= 0);
    }
}
