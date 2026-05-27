package cn.edu.ruc.info.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("notification_reads")
public class NotificationRead {
    private Long userId;
    private String notificationId;
    private LocalDateTime readAt;
}