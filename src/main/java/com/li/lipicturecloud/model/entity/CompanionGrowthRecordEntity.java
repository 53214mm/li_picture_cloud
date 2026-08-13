package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("companion_growth_record")
public class CompanionGrowthRecordEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long feedingRunId;
    private Long companionId;
    private Long pictureId;
    private String eventType;
    private Long lifeExperienceDelta;
    private String traitDeltaJson;
    private String skillDeltaJson;
    private String snapshotJson;
    private String reason;
    private String nutritionMode;
    private Boolean contentUnderstood;
    private String balanceVersion;
    private String idempotencyKey;
    private String correlationId;
    private Date createTime;
}
