package com.li.lipicturecloud.sharding;

import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingValue;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicPictureShardingAlgorithmTest {

    private final DynamicPictureShardingAlgorithm algorithm = new DynamicPictureShardingAlgorithm();
    private final List<String> targets = List.of("picture_0", "picture_1", "picture_2", "picture_3");

    @Test
    void routesSpacePicturesBySpaceId() {
        assertThat(route(Map.of("spaceId", values(6L), "userId", values(99L))))
                .containsExactly("picture_2");
    }

    @Test
    void routesPublicPicturesByUserIdWhenSpaceIdIsAbsent() {
        assertThat(route(Map.of("userId", values(5L))))
                .containsExactly("picture_1");
    }

    @Test
    void broadcastsReadWhenNoRoutingKeyIsAvailable() {
        assertThat(route(Map.of())).containsExactlyElementsOf(targets);
    }

    private Collection<String> route(Map<String, Collection<Comparable<?>>> values) {
        ComplexKeysShardingValue<Comparable<?>> shardingValue =
                new ComplexKeysShardingValue<>("picture", values, Map.of());
        return algorithm.doSharding(targets, shardingValue);
    }

    private Collection<Comparable<?>> values(long value) {
        return List.of(value);
    }
}
