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
            List<ApplicationVO> list = applicationService.listApplications(status);
            return Result.success(list);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping
    public Result<ApplicationVO> create(@RequestBody ApplicationRequest request) {
        try {
            ApplicationVO vo = applicationService.createApplication(request);
            return Result.success(vo);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}