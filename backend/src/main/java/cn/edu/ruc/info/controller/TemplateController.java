package cn.edu.ruc.info.controller;

import cn.edu.ruc.info.common.Result;
import cn.edu.ruc.info.service.TemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/templates")
public class TemplateController {

    @Autowired
    private TemplateService templateService;

    @GetMapping
    public Result<?> list() {
        try {
            return Result.success(templateService.listTemplates());
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable String id) {
        try {
            return Result.success(templateService.getTemplateDetail(id));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/preview")
    public Result<?> preview(@RequestBody Map<String, String> body) {
        try {
            String id = body.get("id");
            return Result.success(templateService.generatePreviewLink(id));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/download")
    public Result<?> download(@RequestBody Map<String, String> body) {
        try {
            String id = body.get("id");
            return Result.success(templateService.generateDownloadLink(id));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}