package cn.edu.ruc.info.controller;

import cn.edu.ruc.info.common.Result;
import cn.edu.ruc.info.service.CurriculumService;
import cn.edu.ruc.info.util.UserContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/curriculum")
public class AdminCurriculumController {

    private final CurriculumService curriculumService;

    public AdminCurriculumController(CurriculumService curriculumService) {
        this.curriculumService = curriculumService;
    }

    @PostMapping("/upload")
    public Result<?> upload(@RequestParam("file") MultipartFile file) {
        try {
            return Result.success(curriculumService.upload(file, UserContext.getUserId()));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/latest")
    public Result<?> latest() {
        try {
            return Result.success(curriculumService.getLatestSummary());
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
