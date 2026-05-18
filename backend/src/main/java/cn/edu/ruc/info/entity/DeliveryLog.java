package cn.edu.ruc.info.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("delivery_logs")
public class DeliveryLog {
    @TableId
    private String id;
    private String title;
    private String audience;
    private String channels;
    private String sentAt;
    private Integer count;
    private String status;
    private Long operatorId;
}