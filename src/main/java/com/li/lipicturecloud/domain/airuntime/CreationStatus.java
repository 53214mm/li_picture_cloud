package com.li.lipicturecloud.domain.airuntime;

/**
 * 创作任务状态机：PENDING → OUTLINING → AWAITING_CONFIRM（大纲）→ DRAFTING
 * → AWAITING_CONFIRM（草稿）→ SAVING → SAVED；任意中间态可 FAILED；
 * AWAITING_CONFIRM 超时 EXPIRED。
 */
public enum CreationStatus {
    PENDING,
    OUTLINING,
    DRAFTING,
    AWAITING_CONFIRM,
    SAVING,
    SAVED,
    FAILED,
    EXPIRED
}
