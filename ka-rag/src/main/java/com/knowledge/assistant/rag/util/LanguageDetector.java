package com.knowledge.assistant.rag.util;

public final class LanguageDetector {

    private static final double CJK_THRESHOLD = 0.2;

    private LanguageDetector() {}

    public static String detect(String text) {
        if (text == null || text.isEmpty()) {
            return "unknown";
        }
        int totalChars = 0;
        int cjkChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c)) {
                continue;
            }
            totalChars++;
            if (isCJK(c)) {
                cjkChars++;
            }
        }
        if (totalChars == 0) {
            return "unknown";
        }
        double ratio = (double) cjkChars / totalChars;
        return ratio >= CJK_THRESHOLD ? "zh" : "en";
    }

    private static boolean isCJK(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA;
    }
}
