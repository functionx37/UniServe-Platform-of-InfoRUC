package cn.edu.ruc.info.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("curriculum_files")
public class CurriculumFile {
    @TableId(type = IdType.INPUT)
    private String id;
    private String fileName;
    private String fileType;
    private String filePath;
    private String version;
    private Boolean active;
    private Long uploadedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime uploadedAt;
}
