package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("companion")
public class CompanionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long lifeExperience;
    private Integer level;
    private String lifeStage;
    private BigDecimal curiosity;
    private BigDecimal enthusiasm;
    private BigDecimal playfulness;
    private BigDecimal empathy;
    private BigDecimal creativity;
    private String balanceVersion;
    private Long revision;
    private Date createTime;
    private Date updateTime;
}
