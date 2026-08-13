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
}
