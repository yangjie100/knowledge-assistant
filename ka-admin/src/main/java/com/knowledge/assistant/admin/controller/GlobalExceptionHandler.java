package com.knowledge.assistant.admin.controller;

import com.knowledge.assistant.common.dto.Result;
import com.knowledge.assistant.common.exception.AiServiceException;
import com.knowledge.assistant.common.exception.DocumentParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

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

    /**
     * M-2 follow-up (review HIGH): must be handled BEFORE the Exception catch-all,
     * otherwise @Valid failures return HTTP 200 + generic 500 body in production
     * (standalone MockMvc tests never see the advice, so this needs its own guard).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", detail);
        return Result.fail(detail);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleGeneral(Exception e) {
        log.error("Unexpected error", e);
        return Result.fail("An unexpected error occurred");
    }
}
