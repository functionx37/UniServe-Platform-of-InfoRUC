package cn.edu.ruc.info.controller;

import cn.edu.ruc.info.common.Result;
import cn.edu.ruc.info.dto.*;
import cn.edu.ruc.info.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @PostMapping("/dashboard")
    public Result<DashboardVO> dashboard(@RequestBody DashboardRequest request) {
        try {
            DashboardVO vo = adminService.getDashboard(request);
            return Result.success(vo);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/notifications")
    public Result<List<NotificationVO>> notifications() {
        try {
            List<NotificationVO> list = adminService.listNotifications();
            return Result.success(list);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/push/logs")
    public Result<List<DeliveryLogVO>> pushLogs() {
        try {
            List<DeliveryLogVO> list = adminService.listDeliveryLogs();
            return Result.success(list);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/import/sessions")
    public Result<List<ImportSessionVO>> importSessions() {
        try {
            List<ImportSessionVO> list = adminService.listImportSessions();
            return Result.success(list);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}