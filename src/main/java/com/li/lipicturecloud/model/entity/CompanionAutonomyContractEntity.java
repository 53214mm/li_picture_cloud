package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.sql.Time;
import java.util.Date;

@Data
@TableName("companion_autonomy_contract")
public class CompanionAutonomyContractEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companionId;
    private Long subjectId;
    private Boolean active;
    private Time quietStart;
    private Time quietEnd;
    private Integer maxFrequencyHours;
    private Long revision;
    private Date createTime;
    private Date updateTime;
}
