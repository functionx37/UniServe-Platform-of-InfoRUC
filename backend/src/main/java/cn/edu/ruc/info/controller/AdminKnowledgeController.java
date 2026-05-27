package cn.edu.ruc.info.controller;

import cn.edu.ruc.info.common.Result;
import cn.edu.ruc.info.service.KnowledgeBaseService;
import cn.edu.ruc.info.util.UserContext;
import lombok.Data;
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

    @PutMapping("/documents/{id}")
    public Result<?> update(@PathVariable String id, @RequestBody UpdateRequest request) {
        try {
            KnowledgeBaseService.UpdateDocumentRequest update = KnowledgeBaseService.UpdateDocumentRequest.builder()
                    .title(request.getTitle())
                    .sourceUrl(request.getSourceUrl())
                    .active(request.getActive())
                    .build();
            return Result.success(knowledgeBaseService.updateDocument(id, update));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/documents/{id}")
    public Result<?> delete(@PathVariable String id) {
        try {
            return Result.success(knowledgeBaseService.deleteDocument(id));
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

    @PostMapping("/bootstrap")
    public Result<?> bootstrap(@RequestParam(value = "dir", required = false) String dir) {
        try {
            return Result.success(knowledgeBaseService.bootstrapFromDirectory(dir, UserContext.getUserId()));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @Data
    public static class UpdateRequest {
        private String title;
        private String sourceUrl;
        private Boolean active;
    }
}
