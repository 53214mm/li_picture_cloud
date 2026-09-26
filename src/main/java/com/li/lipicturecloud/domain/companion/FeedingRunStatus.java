package com.li.lipicturecloud.domain.companion;

/** 一次持久化喂养运行的状态；FAILED 可重启，COMPLETED/REJECTED 为终态。 */
public enum FeedingRunStatus {
    /** 当前有一个尝试拥有处理资格。 */
    PROCESSING,
    /** 成长事务已提交并关联结果记录。 */
    COMPLETED,
    /** 暂时失败，可在策略一致时使用同一幂等键重启。 */
    FAILED,
    /** 因图片不可用或无权限而拒绝，不允许同 key 自动重试。 */
    REJECTED
}
