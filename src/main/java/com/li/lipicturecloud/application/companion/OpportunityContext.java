package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.ProposalOpportunityType;

import java.util.List;
import java.util.Objects;

/**
 * 投递给下游（如阶段 5 配方 WHEN）的类型化机会上下文：真实机会类型 + 本次机会要处理的
 * 目标图片 + 相关空间事实。
 *
 * <p>端口刻意不传"字符串类型 + 单个图片 ID"：只看单个 ID 的下游会拿机会锚点
 * （例如相似图片机会里以前喂养过的那张参照图）当目标图片，从而对旧图执行
 * "空间里出现新的旅行图片时才写故事"这类动作。</p>
 *
 * @param type            真实机会类型（强类型，非字符串）
 * @param anchorPictureId 机会锚点图片（相似图片机会里以前喂养过的参照图），可为 null
 * @param targetPictureIds 本次机会真正要处理的、已授权的目标图片（可为空列表）
 * @param spaceId         机会涉及的空间，可为 null
 * @param recentCount     机会事实里的"近期图片数"，可为 null
 */
public record OpportunityContext(
        ProposalOpportunityType type,
        Long anchorPictureId,
        List<Long> targetPictureIds,
        Long spaceId,
        Long recentCount) {

    public OpportunityContext {
        Objects.requireNonNull(type, "type");
        targetPictureIds = targetPictureIds == null ? List.of() : List.copyOf(targetPictureIds);
    }

    public static OpportunityContext from(OpportunityObservation observation) {
        Objects.requireNonNull(observation, "observation");
        return new OpportunityContext(observation.type(), observation.pictureId(),
                observation.targetPictureIds(), observation.spaceId(), observation.recentCount());
    }
}
