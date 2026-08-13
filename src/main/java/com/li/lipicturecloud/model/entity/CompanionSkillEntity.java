package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("companion_skill")
public class CompanionSkillEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companionId;
    private String skillCode;
    private Long skillExperience;
    private Date createTime;
    private Date updateTime;
}
