package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("model_capability_profile")
public class ModelCapabilityProfileEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long connectionId;
    private Long subjectId;
    private String provider;
    private String modelCode;
    private Boolean text;
    private Boolean vision;
    private Boolean toolCall;
    private Boolean structuredOutput;
    private Boolean reasoning;
    private Boolean embedding;
    private Boolean imageGeneration;
    private Integer maxContextTokens;
    private String syncAsync;
    private String costHint;
    private Date createdTime;
}
