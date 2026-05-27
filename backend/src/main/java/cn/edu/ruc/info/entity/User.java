package cn.edu.ruc.info.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private Integer roleId;          // 1-领导, 2-老师, 3-骨干, 4-学生
    private String realName;
    private String studentNo;
    private String phone;            // 存储密文
    private String idCard;           // 存储密文
    private String grade;
    private String major;
    private String identity;
    private String email;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}