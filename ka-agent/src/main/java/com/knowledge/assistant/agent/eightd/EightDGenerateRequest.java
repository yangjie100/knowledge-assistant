package com.knowledge.assistant.agent.eightd;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/agent/8d/generate — raw text or JSON of a quality
 * abnormal record exported from MES (sanitized).
 */
public record EightDGenerateRequest(
        @NotBlank(message = "质量异常记录不能为空")
        @Size(min = 20, max = 4000, message = "质量异常记录长度须在 20-4000 字符之间")
        String qualityRecord) {
}
