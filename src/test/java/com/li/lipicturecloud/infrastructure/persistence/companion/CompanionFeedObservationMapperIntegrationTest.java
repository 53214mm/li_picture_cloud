package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.lipicturecloud.application.companion.observation.CompanionFeedObservationRow;
import com.li.lipicturecloud.mapper.CompanionFeedObservationMapper;
import com.li.lipicturecloud.model.dto.companion.CompanionFeedObservationQueryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CompanionFeedObservationMapperIntegrationTest {

    @Autowired
    private CompanionFeedObservationMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanRows() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `user` (
                    id BIGINT PRIMARY KEY,
                    userAccount VARCHAR(256),
                    userName VARCHAR(256)
                )
                """);
        jdbcTemplate.update("MERGE INTO `user` (id, userAccount, userName) KEY (id) VALUES (7, 'tester', '测试用户')");
        jdbcTemplate.update("DELETE FROM companion_growth_record WHERE id = 9202");
        jdbcTemplate.update("DELETE FROM companion_feed_run WHERE id IN (9101, 9102)");
    }

    @Test
    void pageReadsSafeRunAndGrowthProjectionInStableOrder() {
        insertRun(9101L, "FAILED", "NUTRITION_FAILED", "2026-08-30 10:00:01");
        insertRun(9102L, "COMPLETED", null, "2026-08-30 10:00:02");
        insertGrowth(9202L);
        jdbcTemplate.update("UPDATE companion_feed_run SET resultGrowthRecordId = 9202 WHERE id = 9102");

        CompanionFeedObservationQueryRequest query = new CompanionFeedObservationQueryRequest();
        query.setPageSize(10);
        Page<CompanionFeedObservationRow> page = new Page<>(1, 10);

        var result = mapper.selectPage(page, query);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).extracting(CompanionFeedObservationRow::runId)
                .containsExactly(9102L, 9101L);
        assertThat(result.getRecords().get(0).safeErrorMessage()).isNull();
        assertThat(result.getRecords().get(0).actualProviderCode()).isEqualTo("internal");
        assertThat(result.getRecords().get(0).growthLifeExperienceDelta()).isEqualTo(42L);
    }

    @Test
    void pageFiltersByStatusAndCorrelationId() {
        insertRun(9101L, "FAILED", "NUTRITION_FAILED", "2026-08-30 10:00:01");
        insertRun(9102L, "COMPLETED", null, "2026-08-30 10:00:02");

        CompanionFeedObservationQueryRequest query = new CompanionFeedObservationQueryRequest();
        query.setStatus("FAILED");
        query.setCorrelationId("00000000-0000-0000-0000-000000009101");
        Page<CompanionFeedObservationRow> page = new Page<>(1, 10);

        var result = mapper.selectPage(page, query);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement()
                .extracting(CompanionFeedObservationRow::runId, CompanionFeedObservationRow::safeErrorCode)
                .containsExactly(9101L, "NUTRITION_FAILED");
    }

    private void insertRun(long id, String status, String safeErrorCode, String timestamp) {
        jdbcTemplate.update("""
                INSERT INTO companion_feed_run
                (id, companionId, subjectId, pictureId, idempotencyKey, requestFingerprint,
                 correlationId, status, requestedPolicy, requestedProviderCode, requestedModelCode,
                 resultGrowthRecordId, safeErrorCode, safeErrorMessage, safeErrorTime,
                 attemptCount, revision, createTime, updateTime)
                VALUES (?, 11, 7, 102, ?, ?, ?, ?, 'METADATA_ONLY', NULL, NULL,
                        NULL, ?, ?, ?,
                        1, 0, ?, ?)
                """, id, "feed-observation-key-" + id,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "00000000-0000-0000-0000-00000000" + id, status, safeErrorCode,
                safeErrorCode == null ? null : "safe failure",
                safeErrorCode == null ? null : Timestamp.valueOf(timestamp),
                Timestamp.valueOf(timestamp), Timestamp.valueOf(timestamp));
    }

    private void insertGrowth(long id) {
        jdbcTemplate.update("""
                INSERT INTO companion_growth_record
                (id, feedingRunId, companionId, pictureId, eventType, lifeExperienceDelta,
                 traitDeltaJson, skillDeltaJson, snapshotJson, reason, nutritionMode,
                 contentUnderstood, balanceVersion, idempotencyKey, correlationId, createTime,
                 providerCode, modelCode, promptVersion, resultSchemaVersion, confidence, fallbackReasonCode)
                VALUES (?, 9102, 11, 102, 'PICTURE_FED', 42, '{}', '{}', '{}', '元数据营养',
                        'METADATA_DETERMINISTIC', FALSE, 'life-core-v1',
                        '6f26d166-0a82-4d9f-8a61-6c21cf2e59d0',
                        '00000000-0000-0000-0000-000000009102', TIMESTAMP '2026-08-30 10:00:02',
                        'internal', 'metadata-v1', 'prompt-v1', 'schema-v1', 0.8000, NULL)
                """, id);
    }
}
