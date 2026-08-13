package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("companion_memory")
public class CompanionMemoryEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companionId;
    private Long subjectId;
    private Long pictureId;
    private Long growthRecordId;
    private String sourceType;
    private String content;
    private String originalContent;
    private BigDecimal confidence;
    private String status;
    private String invalidatedReason;
    private Long revision;
    private Date createTime;
    private Date updateTime;
}
