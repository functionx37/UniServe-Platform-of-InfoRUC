package cn.edu.ruc.info.controller;

import cn.edu.ruc.info.common.Result;
import cn.edu.ruc.info.dto.*;
import cn.edu.ruc.info.entity.AuditLog;
import cn.edu.ruc.info.service.AdminService;
import cn.edu.ruc.info.service.ApplicationService;
import cn.edu.ruc.info.util.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private ApplicationService applicationService;

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

    @PostMapping("/import/notifications")
    public Result<?> importNotifications(@RequestBody List<AdminService.ImportNotificationRow> rows,
            @RequestParam(defaultValue = "notifications.xlsx") String fileName) {
        try {
            requireAdminRole();
            AdminService.ImportNotificationsResult result = adminService.importNotifications(fileName, rows, UserContext.getUserId());
            Result<AdminService.ImportNotificationsResult> response = Result.success(result);
            response.setMessage(result.getMessage());
            return response;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/push/preview")
    public Result<AdminService.PushPreviewVO> previewPush(@RequestBody AdminService.PushFilter filter) {
        try {
            return Result.success(adminService.previewPush(filter));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/push/send")
    public Result<DeliveryLogVO> sendPush(@RequestBody AdminService.SendPushRequest request) {
        try {
            requireAdminRole();
            return Result.success(adminService.sendPush(request, UserContext.getUserId()));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/users")
    public Result<List<AdminService.UserVO>> listUsers(
            @RequestParam(required = false) Integer roleId,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String major,
            @RequestParam(required = false) String keyword) {
        try {
            requireAdminRole();
            AdminService.UserQuery query = new AdminService.UserQuery();
            query.setRoleId(roleId);
            query.setGrade(grade);
            query.setMajor(major);
            query.setKeyword(keyword);
            return Result.success(adminService.listUsers(query));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/users")
    public Result<AdminService.UserVO> createUser(@RequestBody AdminService.CreateUserRequest request) {
        try {
            requireAdminRole();
            return Result.success(adminService.createUser(request, UserContext.getUserId(), UserContext.getRoleId()));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/users/{id}")
    public Result<AdminService.UserVO> updateUser(@PathVariable Long id,
            @RequestBody AdminService.UpdateUserRequest request) {
        try {
            requireAdminRole();
            return Result.success(adminService.updateUser(id, request, UserContext.getUserId(), UserContext.getRoleId()));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/users/{id}")
    public Result<?> deleteUser(@PathVariable Long id) {
        try {
            requireAdminRole();
            adminService.deleteUser(id, UserContext.getUserId(), UserContext.getRoleId());
            return Result.success(null);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping(value = "/users/import", consumes = "application/json")
    public Result<AdminService.ImportUsersResult> importUsers(@RequestBody List<AdminService.ImportUserRow> rows,
            @RequestParam(defaultValue = "users.xlsx") String fileName) {
        try {
            requireAdminRole();
            AdminService.ImportUsersResult result = adminService.importUsers(fileName, rows, UserContext.getUserId(),
                    UserContext.getRoleId());
            Result<AdminService.ImportUsersResult> response = Result.success(result);
            response.setMessage(result.getMessage());
            return response;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping(value = "/users/import", consumes = "multipart/form-data")
    public Result<AdminService.ImportUsersResult> importUsersFile(@RequestParam("file") MultipartFile file) {
        try {
            requireAdminRole();
            AdminService.ImportUsersResult result = adminService.importUsersFromFile(file, UserContext.getUserId(),
                    UserContext.getRoleId());
            Result<AdminService.ImportUsersResult> response = Result.success(result);
            response.setMessage(result.getMessage());
            return response;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/audit")
    public Result<?> auditCompat(@RequestBody Map<String, Object> body) {
        try {
            requireAdminRole();
            Object idObj = body.get("id");
            if (idObj == null) {
                throw new RuntimeException("缺少 id");
            }
            Long id = Long.valueOf(String.valueOf(idObj));
            String action = String.valueOf(body.getOrDefault("action", ""));
            String opinion = String.valueOf(body.getOrDefault("opinion", ""));
            applicationService.auditApplication(id, action, opinion);
            return Result.success(null);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/audit/logs")
    public Result<List<AuditLog>> auditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "20") Integer limit) {
        try {
            requireAdminRole();
            return Result.success(adminService.listAuditLogs(action, limit));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    private void requireAdminRole() {
        Integer role = UserContext.getRoleId();
        if (role == null || (role != 1 && role != 2)) {
            throw new RuntimeException("无权限访问");
        }
    }
}
