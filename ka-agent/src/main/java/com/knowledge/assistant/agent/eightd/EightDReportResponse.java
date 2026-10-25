package com.knowledge.assistant.agent.eightd;

/** Generated 8D report payload. */
public record EightDReportResponse(String format, String report) {

    /** The only format this endpoint produces today; named constant instead of inline literal. */
    public static final String FORMAT_MARKDOWN = "markdown";
}
