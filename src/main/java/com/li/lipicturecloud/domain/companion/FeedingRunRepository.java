package com.li.lipicturecloud.domain.companion;

import java.time.Instant;
import java.util.Optional;

public interface FeedingRunRepository {
    Optional<FeedingRun> findByKey(long companionId, String idempotencyKey);
    FeedingRun insert(FeedingRun run);
    boolean restart(long runId, long expectedRevision, Instant now);
    boolean complete(long runId, long expectedRevision, long growthRecordId, Instant now);
    boolean fail(long runId, long expectedRevision, String safeCode, String safeMessage, Instant now);
    boolean reject(long runId, long expectedRevision, String safeCode, String safeMessage, Instant now);
}
