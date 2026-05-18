package cn.edu.ruc.info.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("transcript_uploads")
public class TranscriptUpload {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String fileName;
    private String fileId;
    private LocalDateTime uploadedAt;
    private Boolean parsed;
}