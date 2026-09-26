package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * companion_skill 表对应的数据库行对象。
 * 每个伙伴的每种技能各占一行，Repository 会把多行组装成领域对象中的技能经验 Map。
 */
@Data
@TableName("companion_skill")
public class CompanionSkillEntity {

    /** 数据库主键；业务上还由“伙伴 id + 技能编码”唯一约束避免重复。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companionId;
    private String skillCode;
    private Long skillExperience;
    private Date createTime;
    private Date updateTime;
}
