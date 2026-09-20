package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("model_connection")
public class ModelConnectionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long subjectId;
    private String provider;
    private String displayName;
    private String endpointUri;
    private String modelCode;
    private Long credentialId;
    private Boolean enabled;
    private Long revision;
    private Date createTime;
    private Date updateTime;
}
