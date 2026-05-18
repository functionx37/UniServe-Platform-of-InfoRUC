package cn.edu.ruc.info.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApplicationVO {
    private Long id;
    private String typeKey;
    private String typeLabel;
    private String title;
    private String status; // 中文状态
    private String form; // JSON
    private String attachments; // JSON
    private String createdAt;
    private String updatedAt;
}