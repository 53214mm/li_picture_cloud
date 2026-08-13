package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.li.lipicturecloud.domain.companion.FeedingRunStatus;
import com.li.lipicturecloud.domain.companion.NutritionPolicy;
import com.li.lipicturecloud.mapper.CompanionFeedRunMapper;
import com.li.lipicturecloud.model.entity.CompanionFeedRunEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MybatisFeedingRunRepositoryTest {

    @Test
    void transitionDoesNotMoveBackwardWhenMysqlRoundedPersistedTimeForward() {
        CompanionFeedRunMapper mapper = mock(CompanionFeedRunMapper.class);
        CompanionFeedRunEntity persisted = processingRow(Instant.parse("2026-08-13T14:54:34Z"));
        when(mapper.selectById(21L)).thenReturn(persisted);
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        MybatisFeedingRunRepository repository = new MybatisFeedingRunRepository(mapper);

        boolean changed = repository.fail(21L, 2L, "FEED_COMMIT_FAILED",
                "本次没有消化成功，图片未被消耗", Instant.parse("2026-08-13T14:54:33.645Z"));

        assertThat(changed).isTrue();
    }

    private static CompanionFeedRunEntity processingRow(Instant updateTime) {
        CompanionFeedRunEntity row = new CompanionFeedRunEntity();
        row.setId(21L);
        row.setCompanionId(11L);
        row.setSubjectId(7L);
        row.setPictureId(102L);
        row.setIdempotencyKey("feeding-time-rounding-04");
        row.setRequestFingerprint("b".repeat(64));
        row.setCorrelationId("fef53056-2d9f-467d-9b1d-1afe9a6638fe");
        row.setStatus(FeedingRunStatus.PROCESSING.name());
        row.setRequestedPolicy(NutritionPolicy.DEMO_ONLY.name());
        row.setAttemptCount(2);
        row.setRevision(2L);
        row.setCreateTime(Date.from(Instant.parse("2026-08-13T14:54:17Z")));
        row.setUpdateTime(Date.from(updateTime));
        return row;
    }
}
