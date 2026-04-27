package com.knowledge.assistant.admin.controller;

import com.knowledge.assistant.common.dto.Result;
import com.knowledge.assistant.common.exception.AiServiceException;
import com.knowledge.assistant.common.exception.DocumentParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DocumentParseException.class)
    public Result<Void> handleDocumentParse(DocumentParseException e) {
        log.warn("Document parse error: {}", e.getMessage());
        return Result.fail("Document parse failed: " + e.getMessage());
    }

    @ExceptionHandler(AiServiceException.class)
    public Result<Void> handleAiService(AiServiceException e) {
        log.error("AI service error: {}", e.getMessage());
        return Result.fail("AI service unavailable");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleGeneral(Exception e) {
        log.error("Unexpected error", e);
        return Result.fail("An unexpected error occurred");
    }
}
