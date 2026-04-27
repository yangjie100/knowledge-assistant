package com.knowledge.assistant.admin.controller;

import com.knowledge.assistant.admin.service.KnowledgeService;
import com.knowledge.assistant.common.dto.Result;
import com.knowledge.assistant.common.model.KnowledgeDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {
    private final KnowledgeService knowledgeService;

    @PostMapping("/upload")
    public Result<KnowledgeDocument> upload(@RequestParam("file") MultipartFile file) {
        try {
            KnowledgeDocument doc = knowledgeService.upload(file.getBytes(), file.getOriginalFilename());
            return Result.ok(doc);
        } catch (Exception e) {
            return Result.fail("File upload failed: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        knowledgeService.delete(id);
        return Result.ok(null);
    }

    @GetMapping("/list")
    public Result<List<KnowledgeDocument>> list() {
        return Result.ok(knowledgeService.list());
    }
}
