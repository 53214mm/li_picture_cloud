package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("companion_mood")
public class CompanionMoodEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companionId;
    private BigDecimal energy;
    private BigDecimal joy;
    private BigDecimal loneliness;
    private BigDecimal inspiration;
    private BigDecimal irritation;
    private Long revision;
    private Date createTime;
    private Date updateTime;
}
