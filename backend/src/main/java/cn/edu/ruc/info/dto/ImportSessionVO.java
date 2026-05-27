package cn.edu.ruc.info.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImportSessionVO {
    private String id;
    private String fileName;
    private Integer totalRows;
    private Integer successRows;
    private Integer failedRows;
    private String importedAt;
}