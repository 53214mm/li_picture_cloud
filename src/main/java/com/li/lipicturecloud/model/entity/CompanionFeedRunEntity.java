package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("companion_feed_run")
public class CompanionFeedRunEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companionId;
    private Long subjectId;
    private Long pictureId;
    private String idempotencyKey;
    private String requestFingerprint;
    private String correlationId;
    private String status;
    private String requestedPolicy;
    private String requestedProviderCode;
    private String requestedModelCode;
    private Long resultGrowthRecordId;
    private String safeErrorCode;
    private String safeErrorMessage;
    private Date safeErrorTime;
    private Integer attemptCount;
    private Long revision;
    private Date createTime;
    private Date updateTime;
}
