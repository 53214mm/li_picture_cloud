package com.li.lipicturecloud.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class H2AnalyticsCompatibilityTest {
    private Connection fixture() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:analytics_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER", "sa", "");
        try {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("e2e-schema.sql"));
            return connection;
        } catch (Exception error) {
            connection.close();
            throw error;
        }
    }

    @Test
    void formatsTheDayAndMonthExpressionsUsedByRealAnalyticsQueries() throws Exception {
        try (Connection connection = fixture(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT DATE_FORMAT(TIMESTAMP '2024-02-29 23:59:59', '%Y-%m-%d'), "
                     + "DATE_FORMAT(TIMESTAMP '2024-02-29 23:59:59', '%Y-%m')")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("2024-02-29");
            assertThat(result.getString(2)).isEqualTo("2024-02");
        }
    }

    @ParameterizedTest
    @CsvSource({"1999-01-01,199852", "2000-01-01,199952", "2023-01-01,202301",
            "2023-12-31,202353", "2024-01-01,202353", "2024-01-06,202353", "2024-01-07,202401"})
    void usesMysqlYearWeekModeZeroAcrossYearBoundaries(String date, int expected) throws Exception {
        try (Connection connection = fixture(); var statement = connection.prepareStatement("SELECT YEARWEEK(CAST(? AS TIMESTAMP))")) {
            statement.setString(1, date + " 12:00:00");
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(expected);
            }
        }
    }
}
