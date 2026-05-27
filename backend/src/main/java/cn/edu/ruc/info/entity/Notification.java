package cn.edu.ruc.info.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("notifications")
public class Notification {
    @TableId
    private String id;
    private String title;
    private String category;
    private String tag;
    private String grade;
    private String major;
    private String channel;
    private String publishAt;
    private String status;
    private String content;
    private String links;            // JSON 字符串
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}