package cn.edu.ruc.info.controller;

import cn.edu.ruc.info.common.Result;
import cn.edu.ruc.info.service.KnowledgeBaseService;
import cn.edu.ruc.info.util.UserContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/admin/knowledge")
public class AdminKnowledgeController {

    private final KnowledgeBaseService knowledgeBaseService;

    public AdminKnowledgeController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping("/documents")
    public Result<?> upload(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "sourceUrl", required = false) String sourceUrl) {
        try {
            return Result.success(knowledgeBaseService.uploadDocument(file, title, sourceUrl, UserContext.getUserId()));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/documents")
    public Result<?> list() {
        try {
            return Result.success(knowledgeBaseService.listDocuments());
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/rebuild")
    public Result<?> rebuild() {
        try {
            int count = knowledgeBaseService.rebuildIndex();
            return Result.success(Map.of("chunkCount", count));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
