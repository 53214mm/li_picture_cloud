package com.li.lipicturecloud.sharding;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShardingModeConfigurationTest {

    @Test
    void singleTableIsTheDefaultMode() throws IOException {
        String config = resource("application.yaml");
        assertThat(config).contains("com.mysql.cj.jdbc.Driver");
        assertThat(config).doesNotContain("jdbc:shardingsphere:");
    }

    @Test
    void staticAndDynamicProfilesPointAtIndependentRules() throws IOException {
        assertThat(resource("application-sharding-static.yaml"))
                .contains("on-profile: sharding-static")
                .contains("classpath:sharding/static.yaml");
        assertThat(resource("application-sharding-dynamic.yaml"))
                .contains("on-profile: sharding-dynamic")
                .contains("classpath:sharding/dynamic.yaml");
        assertThat(resource("sharding/dynamic.yaml"))
                .contains("strategy: COMPLEX")
                .contains(DynamicPictureShardingAlgorithm.class.getName());
    }

    @Test
    void companionMigrationsUsePhysicalDatasourceAndTablesUseSingleRule() throws IOException {
        for (String profile : List.of(
                "application-sharding-static.yaml", "application-sharding-dynamic.yaml")) {
            assertThat(resource(profile))
                    .contains("liquibase:")
                    .contains("driver-class-name: com.mysql.cj.jdbc.Driver")
                    .contains("jdbc:mysql://")
                    .doesNotContain("liquibase:\n    url: jdbc:shardingsphere:");
        }
        for (String rules : List.of("sharding/static.yaml", "sharding/dynamic.yaml")) {
            assertThat(resource(rules))
                    .contains("!SINGLE")
                    .contains("primary.user")
                    .contains("primary.space")
                    .contains("primary.space_user")
                    .contains("primary.companion")
                    .contains("primary.companion_skill")
                    .contains("primary.companion_feed_run")
                    .contains("primary.companion_growth_record")
                    .contains("defaultDataSource: primary");
        }
    }

    private String resource(String name) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
            assertThat(input).as("resource %s", name).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
