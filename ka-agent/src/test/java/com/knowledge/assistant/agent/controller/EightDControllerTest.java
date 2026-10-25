package com.knowledge.assistant.agent.controller;

import com.knowledge.assistant.agent.eightd.EightDReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc tests — @Valid must reject blank/short records with 400;
 * happy path returns Result-wrapped markdown; empty model output degrades to
 * Result.fail instead of an empty success payload.
 */
@ExtendWith(MockitoExtension.class)
class EightDControllerTest {

    @Mock
    private EightDReportService eightDReportService;
    private MockMvc mockMvc;

    private static final String VALID_BODY =
            "{\"qualityRecord\":\"AOI 检出 Chip 元件焊点异常，电测 3 块网络开路，批次暂停流转\"}";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new EightDController(eightDReportService)).build();
    }

    @Test
    void rejectsBlankRecord() throws Exception {
        mockMvc.perform(post("/api/agent/8d/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qualityRecord\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTooShortRecord() throws Exception {
        mockMvc.perform(post("/api/agent/8d/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qualityRecord\":\"过短记录\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsMarkdownReportOnSuccess() throws Exception {
        when(eightDReportService.generateReport(anyString())).thenReturn("# 8D 报告正文");
        mockMvc.perform(post("/api/agent/8d/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.format").value("markdown"))
                .andExpect(jsonPath("$.data.report").value("# 8D 报告正文"));
    }

    @Test
    void returnsFailWhenModelOutputEmpty() throws Exception {
        when(eightDReportService.generateReport(anyString())).thenReturn("");
        mockMvc.perform(post("/api/agent/8d/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }
}
