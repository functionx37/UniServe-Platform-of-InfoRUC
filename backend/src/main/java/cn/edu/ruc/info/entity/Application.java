package cn.edu.ruc.info.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("applications")
public class Application {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String typeKey;
    private String typeLabel;
    private String title;
    private Integer status;          // 0-待审,1-通过,2-驳回,3-撤回
    private String form;             // JSON
    private String attachments;     // JSON

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}