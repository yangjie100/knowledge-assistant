package com.knowledge.assistant.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class DateTimeTool {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (EEEE)");

    @Tool(description = "Get the current date and time, including the day of the week")
    public String getCurrentDateTime() {
        return LocalDateTime.now(ZoneId.systemDefault()).format(FMT);
    }
}
