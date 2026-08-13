package com.li.lipicturecloud.sharding;

import com.zaxxer.hikari.HikariDataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.apache.shardingsphere.driver.api.yaml.YamlShardingSphereDataSourceFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Date;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CompanionSingleTableRoutingIntegrationTest {

    private static final long COMPANION_ID = 8100L;
    private static final long PICTURE_ID = 9100L;
    private static final String FINGERPRINT = "a".repeat(64);
    private static final String CORRELATION_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000").toString();

    @ParameterizedTest
    @ValueSource(strings = {"sharding/static.yaml", "sharding/dynamic.yaml"})
    void routesLegacyAndCompanionSingleTables(String rulesResource) throws Exception {
        HikariDataSource physical = physicalDataSource(rulesResource);
        DataSource routed = null;
        try {
            createLegacyTables(physical);
            migrateCompanionTables(physical);
            routed = YamlShardingSphereDataSourceFactory.createDataSource(
                    Map.of("primary", physical), rulesOnlyBytes(rulesResource));
            try (Connection connection = routed.getConnection()) {
                assertLegacyCrud(connection, "user", 8201L);
                assertLegacyCrud(connection, "space", 8202L);
                assertLegacyCrud(connection, "space_user", 8203L);
                assertCompanionCrud(connection);
            }
        } finally {
            if (routed instanceof AutoCloseable closeable) {
                closeable.close();
            }
            physical.close();
        }
    }

    private HikariDataSource physicalDataSource(String rulesResource) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl("jdbc:h2:mem:routing_" + rulesResource.replaceAll("[^a-zA-Z0-9]", "_")
                + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private void createLegacyTables(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE user (id BIGINT PRIMARY KEY, marker VARCHAR(64) NOT NULL)");
            statement.execute("CREATE TABLE space (id BIGINT PRIMARY KEY, marker VARCHAR(64) NOT NULL)");
            statement.execute("CREATE TABLE space_user (id BIGINT PRIMARY KEY, marker VARCHAR(64) NOT NULL)");
            for (int shard = 0; shard <= 3; shard++) {
                statement.execute("CREATE TABLE picture_" + shard
                        + " (id BIGINT PRIMARY KEY, userId BIGINT NOT NULL, spaceId BIGINT NOT NULL)");
            }
        }
    }

    private void migrateCompanionTables(DataSource dataSource) throws Exception {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:/db/changelog/db.changelog-master.xml");
        liquibase.afterPropertiesSet();
    }

    private void assertLegacyCrud(Connection connection, String table, long id) throws SQLException {
        assertThat(update(connection, "INSERT INTO " + table + " (id, marker) VALUES (?, ?)", id, "created"))
                .isEqualTo(1);
        assertThat(marker(connection, table, id)).isEqualTo("created");
        assertThat(update(connection, "UPDATE " + table + " SET marker = ? WHERE id = ?", "updated", id))
                .isEqualTo(1);
        assertThat(marker(connection, table, id)).isEqualTo("updated");
        assertThat(update(connection, "DELETE FROM " + table + " WHERE id = ?", id)).isEqualTo(1);
    }

    private void assertCompanionCrud(Connection connection) throws SQLException {
        assertThat(update(connection, "INSERT INTO companion (id, userId, balanceVersion) VALUES (?, ?, ?)",
                COMPANION_ID, COMPANION_ID, "life-core-v1")).isEqualTo(1);
        assertThat(update(connection, "UPDATE companion SET lifeExperience = ? WHERE id = ?", 1L, COMPANION_ID))
                .isEqualTo(1);
        assertThat(value(connection, "SELECT lifeExperience FROM companion WHERE id = ?", COMPANION_ID))
                .isEqualTo(1L);

        assertThat(update(connection, "INSERT INTO companion_skill (id, companionId, skillCode) VALUES (?, ?, ?)",
                8101L, COMPANION_ID, "curiosity")).isEqualTo(1);
        assertThat(update(connection, "UPDATE companion_skill SET skillExperience = ? WHERE id = ?", 1L, 8101L))
                .isEqualTo(1);
        assertThat(value(connection, "SELECT skillExperience FROM companion_skill WHERE id = ?", 8101L))
                .isEqualTo(1L);

        assertThat(update(connection, """
                INSERT INTO companion_feed_run (id, companionId, subjectId, pictureId, idempotencyKey,
                requestFingerprint, correlationId, status, requestedPolicy)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, 8102L, COMPANION_ID, COMPANION_ID, PICTURE_ID, "routing-feed-key-01", FINGERPRINT,
                CORRELATION_ID, "PROCESSING", "DEMO_ONLY")).isEqualTo(1);
        assertThat(update(connection, "UPDATE companion_feed_run SET attemptCount = ? WHERE id = ?", 2, 8102L))
                .isEqualTo(1);
        assertThat(value(connection, "SELECT attemptCount FROM companion_feed_run WHERE id = ?", 8102L))
                .isEqualTo(2);

        assertThat(update(connection, """
                INSERT INTO companion_growth_record (id, feedingRunId, companionId, pictureId, eventType,
                lifeExperienceDelta, traitDeltaJson, skillDeltaJson, snapshotJson, reason, nutritionMode,
                contentUnderstood, providerCode, modelCode, promptVersion, resultSchemaVersion,
                balanceVersion, idempotencyKey, correlationId)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, 8103L, 8102L, COMPANION_ID, PICTURE_ID, "FEED", 1L, "{}", "{}", "{}", "created",
                "DEMO_DETERMINISTIC", false, "internal", "demo-v1", "none", "nutrition-v1",
                "life-core-v1", "routing-feed-key-01", CORRELATION_ID)).isEqualTo(1);
        assertThat(update(connection, "UPDATE companion_growth_record SET reason = ? WHERE id = ?", "updated", 8103L))
                .isEqualTo(1);
        assertThat(value(connection, "SELECT reason FROM companion_growth_record WHERE id = ?", 8103L))
                .isEqualTo("updated");

        assertThat(update(connection, """
                INSERT INTO companion_vision_usage (id, subjectId, usageDate, attempts, revision, createTime, updateTime)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, 8104L, COMPANION_ID, Date.valueOf("2026-08-13"), 1, 0L)).isEqualTo(1);
        assertThat(update(connection, "UPDATE companion_vision_usage SET attempts = ? WHERE id = ?", 2, 8104L))
                .isEqualTo(1);
        assertThat(value(connection, "SELECT attempts FROM companion_vision_usage WHERE id = ?", 8104L))
                .isEqualTo(2);

        assertThat(update(connection, "DELETE FROM companion_vision_usage WHERE id = ?", 8104L)).isEqualTo(1);
        assertThat(update(connection, "DELETE FROM companion_growth_record WHERE id = ?", 8103L)).isEqualTo(1);
        assertThat(update(connection, "DELETE FROM companion_feed_run WHERE id = ?", 8102L)).isEqualTo(1);
        assertThat(update(connection, "DELETE FROM companion_skill WHERE id = ?", 8101L)).isEqualTo(1);
        assertThat(update(connection, "DELETE FROM companion WHERE id = ?", COMPANION_ID)).isEqualTo(1);
    }

    private int update(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            return statement.executeUpdate();
        }
    }

    private Object value(Connection connection, String sql, Object parameter) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, parameter);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getObject(1);
            }
        }
    }

    private String marker(Connection connection, String table, long id) throws SQLException {
        return (String) value(connection, "SELECT marker FROM " + table + " WHERE id = ?", id);
    }

    private byte[] rulesOnlyBytes(String name) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
            assertThat(input).as("resource %s", name).isNotNull();
            String yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            int dataSourcesStart = yaml.indexOf("dataSources:");
            int rulesStart = yaml.indexOf("\nrules:");
            assertThat(dataSourcesStart).isGreaterThanOrEqualTo(0);
            assertThat(rulesStart).isGreaterThan(dataSourcesStart);
            String withoutDataSources = yaml.substring(0, dataSourcesStart) + "dataSources: {}\n"
                    + yaml.substring(rulesStart);
            return withoutDataSources.getBytes(StandardCharsets.UTF_8);
        }
    }
}
