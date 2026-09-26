package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * companion 表对应的数据库行对象。
 *
 * <p>它只描述表字段，不承载成长规则。Repository 会把它转换成领域层的 Companion，
 * 由领域对象完成计算后再转换回来保存。</p>
 */
@Data
@TableName("companion")
public class CompanionEntity {

    /** 数据库主键与伙伴归属。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    /** 当前生命经验、等级和阶段的持久化快照。 */
    private Long lifeExperience;
    private Integer level;
    private String lifeStage;
    /** 五项当前情感特质。 */
    private BigDecimal curiosity;
    private BigDecimal enthusiasm;
    private BigDecimal playfulness;
    private BigDecimal empathy;
    private BigDecimal creativity;
    /** 成长公式版本；未来升级规则时可识别旧数据使用的版本。 */
    private String balanceVersion;
    /** 乐观锁版本，每次成功保存伙伴状态后递增。 */
    private Long revision;
    private Date createTime;
    private Date updateTime;
}
