package com.knowledge.assistant.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class CalculatorTool {
    private static final Pattern SAFE = Pattern.compile("^[0-9+\\-*/().\\s]+$");

    @Tool(description = "Evaluate a mathematical expression. Supports +, -, *, /, and parentheses.")
    public String calculate(@ToolParam(description = "Math expression like 2+3*4") String expression) {
        if (expression == null || !SAFE.matcher(expression.trim()).matches()) {
            throw new IllegalArgumentException("Invalid expression: only digits, +, -, *, /, (, ), and decimal point allowed");
        }
        try {
            double result = new Parser(expression.trim().replaceAll("\\s+", "")).parse();
            if (Double.isInfinite(result)) return "Error: division by zero";
            if (result == (long) result) return String.valueOf((long) result);
            return String.valueOf(result);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private static class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
            this.pos = 0;
        }

        double parse() {
            double r = term();
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == '+') { pos++; r += term(); }
                else if (c == '-') { pos++; r -= term(); }
                else break;
            }
            return r;
        }

        double term() {
            double r = factor();
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == '*') { pos++; r *= factor(); }
                else if (c == '/') { pos++; double d = factor(); r = d == 0 ? Double.POSITIVE_INFINITY : r / d; }
                else break;
            }
            return r;
        }

        double factor() {
            if (pos < s.length() && s.charAt(pos) == '(') {
                pos++;
                double r = parse();
                pos++;
                return r;
            }
            int start = pos;
            if (pos < s.length() && (s.charAt(pos) == '-' || s.charAt(pos) == '+')) pos++;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
            return Double.parseDouble(s.substring(start, pos));
        }
    }
}
