package cn.edu.ruc.info.dto;

import lombok.Data;

@Data
public class ApplicationRequest {

    private String typeKey; // leave/enrollment_cert/political_cert
    private Object form; // 表单数据，直接存 JSON
    private String attachments; // 附件 JSON，可选
}