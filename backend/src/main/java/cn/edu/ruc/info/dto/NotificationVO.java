package cn.edu.ruc.info.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationVO {
    private String id;
    private String title;
    private String category;
    private String grade;
    private String major;
    private String channel;
    private String publishAt;
    private String status;
}