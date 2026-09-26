package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * companion_feed_run 表对应的数据库行对象。
 *
 * <p>它保存一次喂养从 PROCESSING 到终态的全过程，是幂等控制和失败恢复的依据。
 * 它不等于最终成长记录；真正发生成长后还会追加一条 GrowthRecord。</p>
 */
@Data
@TableName("companion_feed_run")
public class CompanionFeedRunEntity {

    /** 执行身份、业务对象以及客户端幂等身份。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companionId;
    private Long subjectId;
    private Long pictureId;
    private String idempotencyKey;
    private String requestFingerprint;
    private String correlationId;
    /** 当前状态及请求时确定的分析策略。 */
    private String status;
    private String requestedPolicy;
    private String requestedProviderCode;
    private String requestedModelCode;
    /** 成功时关联成长记录，以便重复请求直接恢复原结果。 */
    private Long resultGrowthRecordId;
    /** 只保存脱敏后的安全错误信息，不保存供应商原始敏感响应。 */
    private String safeErrorCode;
    private String safeErrorMessage;
    private Date safeErrorTime;
    /** 尝试次数和乐观锁版本。 */
    private Integer attemptCount;
    private Long revision;
    private Date createTime;
    private Date updateTime;
}
