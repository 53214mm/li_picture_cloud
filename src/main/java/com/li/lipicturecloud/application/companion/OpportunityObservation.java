package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.ProposalOpportunityType;

import java.util.Objects;

/**
 * 机会观察：机会源在守门之前产生的"轻量候选事实"。
 *
 * <p>观察只做只读验证并确认该类型机会真实存在——每周回顾/纪念日由喂养计数确认，
 * 相似图片由"图片存在 + 当前权限 + 空间近 7 天图片数 ≥ 2"确认。观察结果不携带冲动
 * 评分与文案；相似图片机会会把已确认的图片/空间/数量带给 materialize，后者只做
 * 必要复验、冲动评分与文案生成。守门失败时只记录 type + reason，不发生任何候选级
 * 工作（评分、情绪衰减写回、文案）也不会误报不存在的机会被拦截。</p>
 */
public record OpportunityObservation(
        ProposalOpportunityType type,
        Long pictureId,
        Long spaceId,
        Long recentCount) {

    public OpportunityObservation {
        Objects.requireNonNull(type, "type");
        if (pictureId != null && pictureId <= 0) {
            throw new IllegalArgumentException("pictureId must be positive or null");
        }
        if (spaceId != null && spaceId <= 0) {
            throw new IllegalArgumentException("spaceId must be positive or null");
        }
        if (recentCount != null && recentCount < 0) {
            throw new IllegalArgumentException("recentCount must be nonnegative or null");
        }
    }

    /** 不携带图片/空间事实的通用观察（每周回顾、纪念日）。 */
    public OpportunityObservation(ProposalOpportunityType type) {
        this(type, null, null, null);
    }
}
