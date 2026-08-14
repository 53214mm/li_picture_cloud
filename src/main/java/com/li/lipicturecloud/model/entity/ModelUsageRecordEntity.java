package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("model_usage_record")
public class ModelUsageRecordEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long subjectId;
    private String task;
    private Long connectionId;
    private String provider;
    private String modelCode;
    private String costSource;
    private Boolean success;
    private String safeErrorCode;
    private String correlationId;
    private Date createdTime;
}
