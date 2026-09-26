package com.li.lipicturecloud.domain.companion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 追加式成长事实的持久化端口。
 *
 * <p>除查询和汇总外只暴露 append，避免业务代码修改或删除已经发生的成长历史。</p>
 */
public interface GrowthRecordRepository {
    /** 追加一条成长事实。 */
    GrowthRecord append(GrowthRecord record);

    /** 按喂养运行查找唯一结果，用于幂等回放。 */
    Optional<GrowthRecord> findByFeedingRunId(long feedingRunId);

    /** 查询伙伴最近的成长时间线。 */
    List<GrowthRecord> findRecent(long companionId, int limit);

    /** 判断某图片是否已经产生过首次完整喂养事件。 */
    boolean hasFullFeed(long companionId, long pictureId);

    /** 汇总指定时间点之后获得的生命经验，用于每日上限。 */
    long sumLifeExperienceSince(long companionId, Instant since);

    /** 汇总某图片已经获得的重复熟悉度经验。 */
    long sumRevisitExperience(long companionId, long pictureId);
    /** 某时刻之后的成长记录条数（每周回顾等主动机会的数据源）。 */
    long countSince(long companionId, Instant since);
    /** 往年同月同日（数据库本地日历）的完整喂养次数（纪念日机会源）。 */
    long countAnniversaryFeeds(long companionId, LocalDate anniversaryDate);
    /** 最近完整喂养过的图片 ID（去重、按最近喂养时间倒序，相似图片机会源）。 */
    List<Long> findRecentFedPictureIds(long companionId, int limit);
}
