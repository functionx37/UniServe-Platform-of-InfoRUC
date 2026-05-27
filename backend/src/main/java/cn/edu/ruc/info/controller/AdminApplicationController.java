package cn.edu.ruc.info.controller;

import cn.edu.ruc.info.common.Result;
import cn.edu.ruc.info.service.ApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/applications")
public class AdminApplicationController {

    @Autowired
    private ApplicationService applicationService;

    @GetMapping
    public Result<?> list(@RequestParam(defaultValue = "全部") String status) {
        try {
            return Result.success(applicationService.listApplicationsForAdmin(status));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result<ApplicationService.ApplicationDetailVO> detail(@PathVariable Long id) {
        try {
            return Result.success(applicationService.getApplicationDetail(id));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/{id}/audit")
    public Result<?> audit(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            String action = body.get("action");
            String opinion = body.getOrDefault("opinion", "");
            applicationService.auditApplication(id, action, opinion);
            return Result.success(null);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}