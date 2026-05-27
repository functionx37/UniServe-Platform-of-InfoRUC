package cn.edu.ruc.info.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("academic_records")
public class AcademicRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String courseName;
    private BigDecimal credits;
    private BigDecimal score;
    private String category;
    private String semester;
}