package cn.edu.ruc.info.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardVO {
    private Integer pendingNotificationCount;
    private Integer targetStudentCount;
    private Integer recentDeliveryCount;
    private Integer latestImportSuccessRate;
}