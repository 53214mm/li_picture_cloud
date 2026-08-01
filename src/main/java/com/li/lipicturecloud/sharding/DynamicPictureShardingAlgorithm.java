package com.li.lipicturecloud.sharding;

import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingAlgorithm;
import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Routes space pictures by spaceId and public pictures by userId.
 * A query without either key intentionally broadcasts for correctness.
 */
public final class DynamicPictureShardingAlgorithm
        implements ComplexKeysShardingAlgorithm<Comparable<?>> {

    @Override
    public Collection<String> doSharding(
            Collection<String> availableTargetNames,
            ComplexKeysShardingValue<Comparable<?>> shardingValue
    ) {
        List<String> targets = new ArrayList<>(availableTargetNames);
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("No picture shard is configured");
        }

        Map<String, Collection<Comparable<?>>> values =
                shardingValue.getColumnNameAndShardingValuesMap();
        Long routingKey = firstLong(values.get("spaceId"));
        if (routingKey == null) {
            routingKey = firstLong(values.get("userId"));
        }
        if (routingKey == null) {
            return List.copyOf(targets);
        }

        int suffix = Math.floorMod(routingKey, targets.size());
        String expectedSuffix = "_" + suffix;
        return targets.stream()
                .filter(target -> target.endsWith(expectedSuffix))
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Missing picture shard with suffix " + expectedSuffix));
    }

    private Long firstLong(Collection<Comparable<?>> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        Comparable<?> value = values.iterator().next();
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Picture sharding key must be numeric");
        }
        return number.longValue();
    }

    @Override
    public String getType() {
        return "DYNAMIC_PICTURE";
    }
}
