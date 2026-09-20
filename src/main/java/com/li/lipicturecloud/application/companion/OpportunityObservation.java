package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.ProposalOpportunityType;

import java.util.List;
import java.util.Objects;

/**
 * 机会观察：机会源在守门之前产生的"轻量候选事实"。
 *
 * <p>观察只做只读验证并确认该类型机会真实存在——每周回顾/纪念日由喂养计数确认，
 * 相似图片由"图片存在 + 当前权限 + 空间近 7 天图片数 ≥ 2"确认。观察结果不携带冲动
 * 评分与文案；相似图片机会会把已确认的图片/空间/数量与<b>本次机会真正要处理的目标图片</b>
 * 带给下游，后者只做必要复验、冲动评分与文案生成。守门失败时只记录 type + reason，
 * 不发生任何候选级工作（评分、情绪衰减写回、文案）也不会误报不存在的机会被拦截。</p>
 *
 * <p>{@code targetPictureIds} 是"这次机会要处理哪几张图"，与 {@code pictureId}（机会锚点，
 * 例如相似图片机会里以前喂养过的那张参照图）区分开：下游（配方 IF/THEN、创作任务快照）
 * 必须使用目标图片，而不是锚点图片——否则会拿旧图代替"新出现的那几张图"。</p>
 */
public record OpportunityObservation(
        ProposalOpportunityType type,
        Long pictureId,
        Long spaceId,
        Long recentCount,
        List<Long> targetPictureIds) {

    /** 单个机会最多携带的目标图片数（与创作侧单次上限保持一致）。 */
    public static final int MAX_TARGET_PICTURES = 12;

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
        targetPictureIds = normalizeTargets(targetPictureIds);
    }

    /** 不携带图片/空间事实的通用观察（每周回顾、纪念日）。 */
    public OpportunityObservation(ProposalOpportunityType type) {
        this(type, null, null, null, List.of());
    }

    /** 只携带锚点事实、没有目标图片集合的观察。 */
    public OpportunityObservation(ProposalOpportunityType type, Long pictureId, Long spaceId,
                                  Long recentCount) {
        this(type, pictureId, spaceId, recentCount, List.of());
    }

    private static List<Long> normalizeTargets(List<Long> targets) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = targets.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.size() > MAX_TARGET_PICTURES) {
            throw new IllegalArgumentException("too many target pictures in one observation");
        }
        for (Long pictureId : distinct) {
            if (pictureId <= 0) {
                throw new IllegalArgumentException("target picture ids must be positive");
            }
        }
        return List.copyOf(distinct);
    }
}
