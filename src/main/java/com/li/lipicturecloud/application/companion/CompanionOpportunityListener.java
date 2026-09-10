package com.li.lipicturecloud.application.companion;

import java.time.Instant;

/**
 * 机会触发端口：阶段 3 的机会源观察到真实机会、并且契约/频率/安静时段硬守门通过之后，
 * 由 {@link CompanionProposalService} 通知一次。
 *
 * <p>端口只传"机会类型 + 机会事实"这一个字符串类型名（与
 * {@link com.li.lipicturecloud.domain.companion.ProposalOpportunityType} 同名），
 * 让配方 WHEN（阶段 5）等下游可以复用同一套机会与守门，而不用反向依赖伙伴模块。
 * 监听方必须自守边界：本端口不授权任何自动执行，只允许产生"待用户确认"的结果。</p>
 */
public interface CompanionOpportunityListener {

    /**
     * @param subjectId   机会所属用户
     * @param companionId 伙伴 ID
     * @param whenType    机会类型名（WEEKLY_REVIEW / ANNIVERSARY / SIMILAR_STORY）
     * @param pictureId   机会自带的图片（相似图片机会），可能为 null
     * @param now         机会观察时刻
     */
    void onOpportunity(long subjectId, long companionId, String whenType, Long pictureId,
                       Instant now);
}
