package cn.edu.ruc.info.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("party_stages")
public class PartyStage {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer stageOrder;
    private String title;
    private String description;
    private String defaultTime;
    private String status;
}