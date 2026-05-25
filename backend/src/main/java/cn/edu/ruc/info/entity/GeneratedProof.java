package cn.edu.ruc.info.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("generated_proofs")
public class GeneratedProof {
    @TableId(type = IdType.INPUT)
    private String id;
    private Long applicationId;
    private Long userId;
    private String proofType;
    private String title;
    private String fileName;
    private String filePath;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
