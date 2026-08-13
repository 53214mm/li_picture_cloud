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
            assertCompanionTables(dataSource, 1);

            rollback(dataSource);
            assertCompanionTables(dataSource, 0);

            update(dataSource);
            assertCompanionTables(dataSource, 1);
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
            assertThat(jdbcTemplate.update("DELETE FROM DATABASECHANGELOG WHERE ID = '20260811-01'"))
                    .isEqualTo(1);

            update(dataSource);

            assertCompanionTables(dataSource, 1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID = '20260811-01'", Integer.class))
                    .isEqualTo(1);
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

    private static void update(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor();
        ) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.xml", resourceAccessor, database)) {
                liquibase.update(new Contexts());
            }
        }
    }

    private static void rollback(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor();
        ) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.xml", resourceAccessor, database)) {
                liquibase.rollback(7, new Contexts(), new LabelExpression());
            }
        }
    }

    private static void assertCompanionTables(DataSource dataSource, int expectedCount) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        for (String table : List.of(
                "companion", "companion_skill", "companion_feed_run", "companion_growth_record")) {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                    WHERE LOWER(TABLE_SCHEMA) = 'public' AND LOWER(TABLE_NAME) = ?
                    """, Integer.class, table);
            assertThat(count).as(table).isEqualTo(expectedCount);
        }
    }
}
