package com.li.lipicturecloud.migration;

import com.zaxxer.hikari.HikariDataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanionSchemaMigrationTest {

    @Test
    void updateRollbackAndUpdateAgainLeaveEveryCompanionTableAvailable() throws Exception {
        try (HikariDataSource dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl("jdbc:h2:mem:companion_migration;MODE=MySQL;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");

            update(dataSource);
            assertCompanionTables(dataSource, 1, 1);
            assertMoodRelationshipMemoryTables(dataSource, 1);
            assertChatTables(dataSource, 1);
            assertProposalTables(dataSource, 1);

            rollback(dataSource, proposalChangeSetCount(dataSource));
            // 提案 migration 全部回滚后，其余伙伴表不受影响。
            assertCompanionTables(dataSource, 1, 1);
            assertMoodRelationshipMemoryTables(dataSource, 1);
            assertChatTables(dataSource, 1);
            assertProposalTables(dataSource, 0);

            rollback(dataSource, chatChangeSetCount(dataSource));
            // 对话 migration 全部回滚后，其余伙伴表不受影响。
            assertCompanionTables(dataSource, 1, 1);
            assertMoodRelationshipMemoryTables(dataSource, 1);
            assertChatTables(dataSource, 0);

            rollback(dataSource, moodMemoryChangeSetCount(dataSource));
            // 情绪/关系/记忆 migration 全部回滚后，视觉与初始伙伴表不受影响。
            assertCompanionTables(dataSource, 1, 1);
            assertMoodRelationshipMemoryTables(dataSource, 0);

            rollback(dataSource, visualProviderChangeSetCount(dataSource));
            // 视觉 migration 全部回滚后，初始伙伴四表仍在，后续加入的额度表已消失。
            assertCompanionTables(dataSource, 1, 0);
            assertLegacyContentUnderstoodRemainsNotNull(dataSource);

            update(dataSource);
            rollback(dataSource);
            assertCompanionTables(dataSource, 0, 0);
            assertMoodRelationshipMemoryTables(dataSource, 0);
            assertChatTables(dataSource, 0);
            assertProposalTables(dataSource, 0);

            update(dataSource);
            assertCompanionTables(dataSource, 1, 1);
            assertMoodRelationshipMemoryTables(dataSource, 1);
            assertChatTables(dataSource, 1);
            assertProposalTables(dataSource, 1);
        }
    }

    @Test
    void rerunRecoversWhenDdlCommittedBeforeLiquibaseRecordedTheChangeSet() throws Exception {
        try (HikariDataSource dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl("jdbc:h2:mem:companion_migration_resume;MODE=MySQL;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");

            update(dataSource);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            assertThat(jdbcTemplate.update("DELETE FROM DATABASECHANGELOG WHERE ID = '20260813-10'"))
                    .isEqualTo(1);

            update(dataSource);

            assertCompanionTables(dataSource, 1, 1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID = '20260813-10'", Integer.class))
                    .isEqualTo(1);
        }
    }

    @Test
    void rerunRecoversWhenMysqlCommittedVisionUsageDdlBeforeRecordingIt() throws Exception {
        try (HikariDataSource dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl("jdbc:h2:mem:companion_migration_resume_vision_usage;MODE=MySQL;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");

            update(dataSource);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            assertThat(jdbcTemplate.update("DELETE FROM DATABASECHANGELOG WHERE ID IN ('20260813-17', '20260813-18')"))
                    .isEqualTo(2);

            update(dataSource);

            assertCompanionTables(dataSource, 1, 1);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM DATABASECHANGELOG
                    WHERE ID IN ('20260813-17', '20260813-18')
                    """, Integer.class)).isEqualTo(2);
        }
    }

    @Test
    void existingMalformedTableStopsInsteadOfBeingSilentlyMarkedAsMigrated() throws Exception {
        try (HikariDataSource dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl("jdbc:h2:mem:companion_migration_malformed;MODE=MySQL;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            new JdbcTemplate(dataSource).execute("CREATE TABLE companion (id BIGINT PRIMARY KEY)");

            assertThatThrownBy(() -> update(dataSource))
                    .hasMessageContaining("Precondition");
        }
    }

    @Test
    void upgradesLegacyRunsToRequestedPoliciesAndBackfillsGrowthProvenance() throws Exception {
        try (HikariDataSource dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl("jdbc:h2:mem:companion_provenance_upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");

            update(dataSource, "db/changelog/changes/2026-08-11-companion-life-core.xml");
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            insertLegacyRun(jdbcTemplate, 101L, "DEMO_DETERMINISTIC");
            insertLegacyRun(jdbcTemplate, 102L, "METADATA_DETERMINISTIC");
            jdbcTemplate.update("""
                    INSERT INTO companion_growth_record
                    (id, feedingRunId, companionId, pictureId, eventType, lifeExperienceDelta,
                     traitDeltaJson, skillDeltaJson, snapshotJson, reason, nutritionMode,
                     contentUnderstood, balanceVersion, idempotencyKey, correlationId, createTime)
                    VALUES (201, 101, 11, 102, 'PICTURE_FED', 42, '{}', '{}', '{}', 'legacy',
                            'DEMO_DETERMINISTIC', FALSE, 'life-core-v1',
                            '6f26d166-0a82-4d9f-8a61-6c21cf2e59d0',
                            'fef53056-2d9f-467d-9b1d-1afe9a6638fe', CURRENT_TIMESTAMP)
                    """);

            update(dataSource);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT requestedPolicy FROM companion_feed_run WHERE id = 101", String.class))
                    .isEqualTo("DEMO_ONLY");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT requestedPolicy FROM companion_feed_run WHERE id = 102", String.class))
                    .isEqualTo("METADATA_ONLY");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT providerCode FROM companion_growth_record WHERE id = 201", String.class))
                    .isEqualTo("internal");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT modelCode FROM companion_growth_record WHERE id = 201", String.class))
                    .isEqualTo("demo-v1");
            assertThat(columnExists(jdbcTemplate, "companion_feed_run", "requestedProviderCode")).isTrue();
            assertThat(columnExists(jdbcTemplate, "companion_feed_run", "requestedModelCode")).isTrue();
            assertThat(columnExists(jdbcTemplate, "companion_growth_record", "promptVersion")).isTrue();
            assertThat(columnExists(jdbcTemplate, "companion_growth_record", "resultSchemaVersion")).isTrue();
            assertThat(columnExists(jdbcTemplate, "companion_growth_record", "confidence")).isTrue();
            assertThat(columnExists(jdbcTemplate, "companion_growth_record", "fallbackReasonCode")).isTrue();
        }
    }

    @Test
    void resumesWhenMysqlCommittedTheFeedRunRenameBeforeLiquibaseRecordedIt() throws Exception {
        try (HikariDataSource dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl("jdbc:h2:mem:companion_provenance_resume_rename;MODE=MySQL;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");

            update(dataSource, "db/changelog/changes/2026-08-11-companion-life-core.xml");
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.execute("ALTER TABLE companion_feed_run RENAME COLUMN nutritionMode TO requestedPolicy");

            update(dataSource);

            assertThat(columnExists(jdbcTemplate, "companion_feed_run", "requestedProviderCode")).isTrue();
            assertThat(columnExists(jdbcTemplate, "companion_feed_run", "requestedModelCode")).isTrue();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID = '20260813-01'", Integer.class))
                    .isEqualTo(1);
        }
    }

    @Test
    void stopsMigrationWhenLegacyNutritionModeIsNotOneOfTheKnownValues() throws Exception {
        try (HikariDataSource dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl("jdbc:h2:mem:companion_provenance_invalid_legacy;MODE=MySQL;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");

            update(dataSource, "db/changelog/changes/2026-08-11-companion-life-core.xml");
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            insertLegacyRun(jdbcTemplate, 301L, "UNKNOWN_LEGACY_MODE");

            assertThatThrownBy(() -> update(dataSource))
                    .hasMessageContaining("Expected '0' got '1'");
        }
    }

    @Test
    void stopsMigrationWhenLegacyGrowthModeIsNotOneOfTheKnownValues() throws Exception {
        try (HikariDataSource dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl("jdbc:h2:mem:companion_provenance_invalid_legacy_growth;MODE=MySQL;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");

            update(dataSource, "db/changelog/changes/2026-08-11-companion-life-core.xml");
            insertLegacyGrowth(new JdbcTemplate(dataSource), 401L, "UNKNOWN_LEGACY_MODE");

            assertThatThrownBy(() -> update(dataSource))
                    .hasMessageContaining("Expected '0' got '1'");
        }
    }

    private static void update(DataSource dataSource) throws Exception {
        update(dataSource, "db/changelog/db.changelog-master.xml");
    }

    private static void update(DataSource dataSource, String changeLog) throws Exception {
        try (Connection connection = dataSource.getConnection();
             ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor();
        ) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(
                    changeLog, resourceAccessor, database)) {
                liquibase.update(new Contexts());
            }
        }
    }

    private static void rollback(DataSource dataSource) throws Exception {
        int appliedChangeSets = new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM DATABASECHANGELOG", Integer.class);
        rollback(dataSource, appliedChangeSets);
    }

    private static void rollback(DataSource dataSource, int changeSetCount) throws Exception {
        try (Connection connection = dataSource.getConnection();
             ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor();
        ) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.xml", resourceAccessor, database)) {
                liquibase.rollback(changeSetCount, new Contexts(), new LabelExpression());
            }
        }
    }

    private static void assertCompanionTables(DataSource dataSource, int expectedCoreTableCount,
                                              int expectedVisionUsageTableCount) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        for (String table : List.of(
                "companion", "companion_skill", "companion_feed_run", "companion_growth_record")) {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                    WHERE LOWER(TABLE_SCHEMA) = 'public' AND LOWER(TABLE_NAME) = ?
                    """, Integer.class, table);
            assertThat(count).as(table).isEqualTo(expectedCoreTableCount);
        }
        Integer visionUsageCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                WHERE LOWER(TABLE_SCHEMA) = 'public' AND LOWER(TABLE_NAME) = 'companion_vision_usage'
                """, Integer.class);
        assertThat(visionUsageCount).as("companion_vision_usage").isEqualTo(expectedVisionUsageTableCount);
    }

    private static void insertLegacyRun(JdbcTemplate jdbcTemplate, long id, String nutritionMode) {
        jdbcTemplate.update("""
                INSERT INTO companion_feed_run
                (id, companionId, subjectId, pictureId, idempotencyKey, requestFingerprint,
                 correlationId, status, nutritionMode, contentUnderstood, resultGrowthRecordId,
                 safeErrorCode, safeErrorMessage, safeErrorTime, attemptCount, revision, createTime, updateTime)
                VALUES (?, 11, 7, 102, ?,
                        'f874b3c9fcbec3f749fe12d7ea01bcf09b83244cbe3b16745486df590f3ec97d',
                        'fef53056-2d9f-467d-9b1d-1afe9a6638fe', 'PROCESSING', ?, FALSE,
                        NULL, NULL, NULL, NULL, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, "legacy-feed-key-" + id, nutritionMode);
    }

    private static boolean columnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE LOWER(TABLE_SCHEMA) = 'public' AND LOWER(TABLE_NAME) = LOWER(?)
                  AND LOWER(COLUMN_NAME) = LOWER(?)
                """, Integer.class, tableName, columnName);
        return count != null && count == 1;
    }

    private static void insertLegacyGrowth(JdbcTemplate jdbcTemplate, long id, String nutritionMode) {
        jdbcTemplate.update("""
                INSERT INTO companion_growth_record
                (id, feedingRunId, companionId, pictureId, eventType, lifeExperienceDelta,
                 traitDeltaJson, skillDeltaJson, snapshotJson, reason, nutritionMode,
                 contentUnderstood, balanceVersion, idempotencyKey, correlationId, createTime)
                VALUES (?, 101, 11, 102, 'PICTURE_FED', 42, '{}', '{}', '{}', 'legacy', ?, FALSE,
                        'life-core-v1', '6f26d166-0a82-4d9f-8a61-6c21cf2e59d0',
                        'fef53056-2d9f-467d-9b1d-1afe9a6638fe', CURRENT_TIMESTAMP)
                """, id, nutritionMode);
    }

    private static int visualProviderChangeSetCount(DataSource dataSource) {
        return new JdbcTemplate(dataSource).queryForObject("""
                SELECT COUNT(*) FROM DATABASECHANGELOG
                WHERE FILENAME LIKE '%2026-08-13-companion-visual-provider.xml'
                """, Integer.class);
    }

    private static int moodMemoryChangeSetCount(DataSource dataSource) {
        return new JdbcTemplate(dataSource).queryForObject("""
                SELECT COUNT(*) FROM DATABASECHANGELOG
                WHERE FILENAME LIKE '%2026-08-14-companion-mood-relationship-memory.xml'
                """, Integer.class);
    }

    private static int chatChangeSetCount(DataSource dataSource) {
        return new JdbcTemplate(dataSource).queryForObject("""
                SELECT COUNT(*) FROM DATABASECHANGELOG
                WHERE FILENAME LIKE '%2026-08-14-companion-chat.xml'
                """, Integer.class);
    }

    private static int proposalChangeSetCount(DataSource dataSource) {
        return new JdbcTemplate(dataSource).queryForObject("""
                SELECT COUNT(*) FROM DATABASECHANGELOG
                WHERE FILENAME LIKE '%2026-08-14-companion-proposal.xml'
                """, Integer.class);
    }

    private static void assertMoodRelationshipMemoryTables(DataSource dataSource, int expectedTableCount) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        for (String table : List.of("companion_mood", "companion_relationship", "companion_memory")) {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                    WHERE LOWER(TABLE_SCHEMA) = 'public' AND LOWER(TABLE_NAME) = ?
                    """, Integer.class, table);
            assertThat(count).as(table).isEqualTo(expectedTableCount);
        }
    }

    private static void assertChatTables(DataSource dataSource, int expectedTableCount) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        for (String table : List.of("companion_chat_message", "companion_chat_usage")) {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                    WHERE LOWER(TABLE_SCHEMA) = 'public' AND LOWER(TABLE_NAME) = ?
                    """, Integer.class, table);
            assertThat(count).as(table).isEqualTo(expectedTableCount);
        }
    }

    private static void assertProposalTables(DataSource dataSource, int expectedTableCount) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        for (String table : List.of("companion_autonomy_contract", "companion_proposal",
                "companion_proposal_reaction")) {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                    WHERE LOWER(TABLE_SCHEMA) = 'public' AND LOWER(TABLE_NAME) = ?
                    """, Integer.class, table);
            assertThat(count).as(table).isEqualTo(expectedTableCount);
        }
    }

    private static void assertLegacyContentUnderstoodRemainsNotNull(DataSource dataSource) {
        String nullable = new JdbcTemplate(dataSource).queryForObject("""
                SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS
                WHERE LOWER(TABLE_SCHEMA) = 'public' AND LOWER(TABLE_NAME) = 'companion_feed_run'
                  AND LOWER(COLUMN_NAME) = 'contentunderstood'
                """, String.class);
        assertThat(nullable).isEqualTo("NO");
    }
}
