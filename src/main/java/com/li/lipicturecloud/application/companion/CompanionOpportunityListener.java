package com.li.lipicturecloud.application.companion;

import java.time.Instant;

/**
 * 机会触发端口：阶段 3 的机会源观察到真实机会、并且契约/频率/安静时段硬守门通过之后，
 * 由 {@link CompanionProposalService} 通知一次。
 *
 * <p>端口传递类型化的 {@link OpportunityContext}（机会类型 + 本次真实目标图片 + 空间事实），
 * 让配方 WHEN（阶段 5）等下游可以复用同一套机会与守门，而不用反向依赖伙伴模块。
 * 下游必须使用上下文里的<b>目标图片</b>（而不是锚点图片）来求值与执行。
 * 监听方自己守边界：本端口不授权任何自动执行，只允许产生"待用户确认"的结果。</p>
 */
public interface CompanionOpportunityListener {

    /**
     * @param subjectId 机会所属用户
     * @param companionId 伙伴 ID
     * @param context 类型化机会上下文（类型、目标图片、空间事实）
     * @param now 机会观察时刻
     */
    void onOpportunity(long subjectId, long companionId, OpportunityContext context, Instant now);
}
