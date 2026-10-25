package com.knowledge.assistant.agent.controller;

import com.knowledge.assistant.agent.eightd.EightDGenerateRequest;
import com.knowledge.assistant.agent.eightd.EightDReportResponse;
import com.knowledge.assistant.agent.eightd.EightDReportService;
import com.knowledge.assistant.common.dto.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo-oriented endpoint: quality abnormal record in, 8D report (markdown) out.
 * The report is a draft for engineer review — auxiliary, not a replacement.
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class EightDController {

    private final EightDReportService eightDReportService;

    @PostMapping("/8d/generate")
    public Result<EightDReportResponse> generate(@Valid @RequestBody EightDGenerateRequest request) {
        String report = eightDReportService.generateReport(request.qualityRecord());
        if (report.isBlank()) {
            return Result.fail("8D 报告生成失败：模型未返回有效内容");
        }
        return Result.ok(new EightDReportResponse(EightDReportResponse.FORMAT_MARKDOWN, report));
    }
}
