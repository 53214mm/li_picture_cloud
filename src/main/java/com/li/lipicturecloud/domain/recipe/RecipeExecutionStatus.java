package com.li.lipicturecloud.domain.recipe;

/**
 * 配方执行状态。
 *
 * <ul>
 *     <li>{@code DRY_RUN}：用户手动试运行，等待确认执行；</li>
 *     <li>{@code PENDING_CONFIRM}：机会源命中 WHEN 后生成的待确认执行（阶段 3 守门已通过，
 *         只报价与求值，不调用任何能力），等待用户确认；</li>
 *     <li>{@code EXECUTED} / {@code FAILED} / {@code REJECTED}：终态。</li>
 * </ul>
 */
public enum RecipeExecutionStatus {
    DRY_RUN,
    PENDING_CONFIRM,
    EXECUTED,
    FAILED,
    REJECTED
}
