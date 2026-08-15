package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("creation_fusion_image")
public class CreationFusionImageEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long taskId;
    private String mimeType;
    private byte[] bytes;
    private Date createdTime;
}
