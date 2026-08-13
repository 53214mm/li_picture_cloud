package com.li.lipicturecloud.domain.companion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GrowthRecordRepository {
    GrowthRecord append(GrowthRecord record);
    Optional<GrowthRecord> findByFeedingRunId(long feedingRunId);
    List<GrowthRecord> findRecent(long companionId, int limit);
    boolean hasFullFeed(long companionId, long pictureId);
    long sumLifeExperienceSince(long companionId, Instant since);
    long sumRevisitExperience(long companionId, long pictureId);
    /** 某时刻之后的成长记录条数（每周回顾等主动机会的数据源）。 */
    long countSince(long companionId, Instant since);
}
