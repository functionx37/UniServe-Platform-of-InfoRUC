package cn.edu.ruc.info.controller;

import cn.edu.ruc.info.common.Result;
import cn.edu.ruc.info.service.AcademicService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/academic")
public class AcademicController {

    @Autowired
    private AcademicService academicService;

    @GetMapping("/status")
    public Result<AcademicService.OverviewVO> status() {
        try {
            return Result.success(academicService.getOverview());
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/analysis")
    public Result<AcademicService.AnalysisVO> analysis() {
        try {
            return Result.success(academicService.getAnalysis());
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/transcript/upload")
    public Result<AcademicService.TranscriptInfo> uploadTranscript(@RequestParam("file") MultipartFile file) {
        try {
            return Result.success(academicService.uploadTranscript(file));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}