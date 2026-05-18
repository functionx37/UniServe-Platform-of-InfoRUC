package cn.edu.ruc.info.controller;

import cn.edu.ruc.info.common.Result;
import cn.edu.ruc.info.dto.ApplicationRequest;
import cn.edu.ruc.info.dto.ApplicationVO;
import cn.edu.ruc.info.service.ApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/applications")
public class ApplicationController {

    @Autowired
    private ApplicationService applicationService;

    @GetMapping
    public Result<List<ApplicationVO>> list(@RequestParam(defaultValue = "全部") String status) {
        try {
            return Result.success(applicationService.listApplications(status));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping
    public Result<ApplicationVO> create(@RequestBody ApplicationRequest request) {
        try {
            return Result.success(applicationService.createApplication(request));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    // 新增：查看申请详情
    @GetMapping("/{id}")
    public Result<ApplicationService.ApplicationDetailVO> detail(@PathVariable Long id) {
        try {
            return Result.success(applicationService.getApplicationDetail(id));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}