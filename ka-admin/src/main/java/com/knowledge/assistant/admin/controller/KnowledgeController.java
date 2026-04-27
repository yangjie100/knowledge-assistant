package com.knowledge.assistant.admin.controller;

import com.knowledge.assistant.admin.service.KnowledgeService;
import com.knowledge.assistant.common.dto.Result;
import com.knowledge.assistant.common.model.KnowledgeDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {
    private final KnowledgeService knowledgeService;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("txt", "md", "pdf", "html");

    @PostMapping("/upload")
    public Result<KnowledgeDocument> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail("File must not be empty");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            return Result.fail("Filename must not be empty");
        }
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return Result.fail("File type not allowed. Supported: " + ALLOWED_EXTENSIONS);
        }
        try {
            KnowledgeDocument doc = knowledgeService.upload(file.getBytes(), filename);
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
