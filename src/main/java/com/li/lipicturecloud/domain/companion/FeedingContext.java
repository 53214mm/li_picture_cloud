package com.li.lipicturecloud.domain.companion;

/**
 * 领域计算一次喂养时需要的历史汇总事实。
 *
 * <p>这些值由应用/仓储在伙伴行锁内查询，领域对象据此执行每日上限和重复图片规则。</p>
 */
public record FeedingContext(
        boolean picturePreviouslyFed,
        long lifeExperienceEarnedToday,
        long revisitExperienceEarnedForPicture) {

    public FeedingContext {
        if (lifeExperienceEarnedToday < 0 || revisitExperienceEarnedForPicture < 0) {
            throw new IllegalArgumentException("feeding totals must be nonnegative");
        }
    }
}
