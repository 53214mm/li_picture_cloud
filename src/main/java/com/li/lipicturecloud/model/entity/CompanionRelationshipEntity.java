package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("companion_relationship")
public class CompanionRelationshipEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companionId;
    private Long subjectId;
    private BigDecimal familiarity;
    private BigDecimal trust;
    private BigDecimal closeness;
    private BigDecimal tacit;
    private BigDecimal recentFeedback;
    private Long revision;
    private Date createTime;
    private Date updateTime;
}
