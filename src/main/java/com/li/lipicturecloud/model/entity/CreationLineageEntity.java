package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("creation_lineage")
public class CreationLineageEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long taskId;
    private Long sourcePictureId;
    private String capabilityId;
    private String modelCode;
    private String promptTemplateVersion;
    private String costSource;
    private Date createdTime;
}
