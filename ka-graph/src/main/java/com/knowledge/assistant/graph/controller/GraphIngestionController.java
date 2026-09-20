package com.knowledge.assistant.graph.controller;

import com.knowledge.assistant.common.dto.Result;
import com.knowledge.assistant.graph.model.IngestionStats;
import com.knowledge.assistant.graph.service.GraphIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual trigger for one-shot graph backfill. Each chunk costs one GLM extraction call, so
 * ingestion is NOT auto-run on boot — call POST /api/graph/ingest explicitly after enabling
 * ka.graph.enabled and after documents are uploaded. Conditional on ka.graph.enabled.
 */
@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ka.graph.enabled", havingValue = "true")
public class GraphIngestionController {

    private final GraphIngestionService graphIngestionService;

    @PostMapping("/ingest")
    public Result<IngestionStats> ingest() {
        return Result.ok(graphIngestionService.ingestAll());
    }
}
