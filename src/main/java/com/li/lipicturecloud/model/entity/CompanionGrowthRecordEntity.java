package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * companion_growth_record 表对应的数据库行对象。
 *
 * <p>每行表示已经发生的一次成长事实。它既保存本次增量，也保存成长后的伙伴快照和分析来源，
 * 因而能够追溯“为什么成长成这样”。该表按设计只追加，不改写历史。</p>
 */
@Data
@TableName("companion_growth_record")
public class CompanionGrowthRecordEntity {

    /** 成长事实身份及其关联的喂养执行、伙伴和图片。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long feedingRunId;
    private Long companionId;
    private Long pictureId;
    /** 本次成长事件及产生的经验、特质和技能增量。 */
    private String eventType;
    private Long lifeExperienceDelta;
    private String traitDeltaJson;
    private String skillDeltaJson;
    /** 成长后的伙伴快照 JSON，用于历史展示，不作为当前状态来源。 */
    private String snapshotJson;
    private String reason;
    /** 图片营养分析的真实来源、版本、置信度及降级原因。 */
    private String nutritionMode;
    private Boolean contentUnderstood;
    private String providerCode;
    private String modelCode;
    private String promptVersion;
    private String resultSchemaVersion;
    private java.math.BigDecimal confidence;
    private String fallbackReasonCode;
    /** 规则版本、幂等键和链路 id 用于审计与排查。 */
    private String balanceVersion;
    private String idempotencyKey;
    private String correlationId;
    private Date createTime;
}
