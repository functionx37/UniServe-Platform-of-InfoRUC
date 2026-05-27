package cn.edu.ruc.info.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("import_sessions")
public class ImportSession {
    @TableId
    private String id;
    private String fileName;
    private Integer totalRows;
    private Integer successRows;
    private Integer failedRows;
    private String importedAt;
    private Long operatorId;
}