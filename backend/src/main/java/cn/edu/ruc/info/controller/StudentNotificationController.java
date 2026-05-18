package cn.edu.ruc.info.controller;

import cn.edu.ruc.info.common.Result;
import cn.edu.ruc.info.service.NotificationStudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/notifications")
public class StudentNotificationController {

    @Autowired
    private NotificationStudentService notificationStudentService;

    @GetMapping
    public Result<?> list(@RequestParam(defaultValue = "全部") String tag) {
        try {
            return Result.success(notificationStudentService.listNotifications(tag));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable String id) {
        try {
            return Result.success(notificationStudentService.getDetail(id));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/read")
    public Result<?> markRead(@RequestBody Map<String, String> body) {
        try {
            String id = body.get("id");
            notificationStudentService.markAsRead(id);
            return Result.success(null);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}