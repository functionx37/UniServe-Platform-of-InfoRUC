package cn.edu.ruc.info.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("approval_records")
public class ApprovalRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicationId;
    private Long approverId;
    private String stepTitle;
    private Integer status;          // 1-通过,2-驳回
    private String opinion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}