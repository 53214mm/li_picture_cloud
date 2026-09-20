package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 机会源 SQL（纪念日月日匹配、最近喂养图片去重）在 H2 上的真实执行验证。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CompanionOpportunityQueryIntegrationTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private GrowthRecordRepository growthRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void anniversaryQueryMatchesOnlyPreviousYearsOnSameMonthAndDay() {
        long companionId = 901L;
        LocalDate today = LocalDate.now(SHANGHAI);
        LocalDate lastYearSameDay = today.minusYears(1);
        LocalDate thisYearSameDay = today; // 今年的同日不应计入"往年"
        insertGrowth(companionId, 1001L, 1L, lastYearSameDay);
        insertGrowth(companionId, 1002L, 2L, lastYearSameDay);
        insertGrowth(companionId, 1003L, 3L, thisYearSameDay);
        insertGrowth(companionId, 1004L, 4L, today.minusMonths(3));

        long count = growthRepository.countAnniversaryFeeds(companionId,
                today.getMonthValue(), today.getDayOfMonth());

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void recentFedPictureIdsDeduplicatesAndOrdersByLatestFeed() {
        long companionId = 902L;
        Instant base = Instant.parse("2026-08-01T00:00:00Z");
        insertGrowth(companionId, 2001L, 1L, base);
        insertGrowth(companionId, 2002L, 2L, base.plusSeconds(3600));
        insertGrowth(companionId, 2001L, 3L, base.plusSeconds(7200)); // 2001 第二次喂养（最新）

        List<Long> ids = growthRepository.findRecentFedPictureIds(companionId, 5);

        assertThat(ids).containsExactly(2001L, 2002L);
    }

    private void insertGrowth(long companionId, long pictureId, long runId, LocalDate date) {
        Instant created = date.atStartOfDay(SHANGHAI).toInstant();
        jdbcTemplate.update("""
                INSERT INTO companion_growth_record
                (id, feedingRunId, companionId, pictureId, eventType, lifeExperienceDelta,
                 traitDeltaJson, skillDeltaJson, snapshotJson, reason, nutritionMode,
                 contentUnderstood, providerCode, modelCode, promptVersion, resultSchemaVersion,
                 balanceVersion, idempotencyKey, correlationId, createTime)
                VALUES (?, ?, ?, ?, 'PICTURE_FED', 42, '{}', '{}', '{}', 'test',
                        'DEMO_DETERMINISTIC', FALSE, 'internal', 'demo-v1', 'none', 'nutrition-v1',
                        'life-core-v1',
                        '6f26d166-0a82-4d9f-8a61-6c21cf2e59d0',
                        'fef53056-2d9f-467d-9b1d-1afe9a6638fe', ?)
                """, runId * 1000 + pictureId % 10, runId, companionId, pictureId,
                java.sql.Timestamp.from(created));
    }

    private void insertGrowth(long companionId, long pictureId, long runId, Instant created) {
        jdbcTemplate.update("""
                INSERT INTO companion_growth_record
                (id, feedingRunId, companionId, pictureId, eventType, lifeExperienceDelta,
                 traitDeltaJson, skillDeltaJson, snapshotJson, reason, nutritionMode,
                 contentUnderstood, providerCode, modelCode, promptVersion, resultSchemaVersion,
                 balanceVersion, idempotencyKey, correlationId, createTime)
                VALUES (?, ?, ?, ?, 'PICTURE_FED', 42, '{}', '{}', '{}', 'test',
                        'DEMO_DETERMINISTIC', FALSE, 'internal', 'demo-v1', 'none', 'nutrition-v1',
                        'life-core-v1',
                        '6f26d166-0a82-4d9f-8a61-6c21cf2e59d0',
                        'fef53056-2d9f-467d-9b1d-1afe9a6638fe', ?)
                """, runId * 1000 + pictureId % 10, runId, companionId, pictureId,
                java.sql.Timestamp.from(created));
    }
}
