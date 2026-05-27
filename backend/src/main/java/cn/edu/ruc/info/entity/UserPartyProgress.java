package cn.edu.ruc.info.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_party_progress")
public class UserPartyProgress {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer stageId;
    private Boolean completed;
    private LocalDateTime completedAt;
    private String notes;
}