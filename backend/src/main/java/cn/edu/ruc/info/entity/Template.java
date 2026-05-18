package cn.edu.ruc.info.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("templates")
public class Template {
    @TableId
    private String id;
    private String title;
    private String scene;
    private String fileType;
    private String url;
    private LocalDateTime updatedAt;
}