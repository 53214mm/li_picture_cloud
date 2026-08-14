package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("creation_task")
public class CreationTaskEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long subjectId;
    private String kind;
    private String sourcePictureIds;
    private String status;
    private String outlineText;
    private String draftText;
    private String resultText;
    private Long modelConnectionId;
    private String idempotencyKey;
    private Long revision;
    private Date createTime;
    private Date updateTime;
}
