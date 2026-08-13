package com.li.lipicturecloud.domain.companion;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record GrowthRecord(
        Long id,
        long feedingRunId,
        long companionId,
        long pictureId,
        GrowthEventType eventType,
        long lifeExperienceDelta,
        TraitDelta traitDelta,
        Map<CompanionSkill, Long> skillExperienceDelta,
        Companion companionAfter,
        String reason,
        NutritionMode nutritionMode,
        boolean contentUnderstood,
        String balanceVersion,
        String idempotencyKey,
        String correlationId,
        Instant createdTime) {

    public GrowthRecord {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (feedingRunId <= 0 || companionId <= 0 || pictureId <= 0 || lifeExperienceDelta < 0) {
            throw new IllegalArgumentException("invalid growth record identity or delta");
        }
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(traitDelta, "traitDelta");
        Objects.requireNonNull(skillExperienceDelta, "skillExperienceDelta");
        if (skillExperienceDelta.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0)) {
            throw new IllegalArgumentException("skill experience delta must be nonnegative");
        }
        skillExperienceDelta = Map.copyOf(skillExperienceDelta);
        Objects.requireNonNull(companionAfter, "companionAfter");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(nutritionMode, "nutritionMode");
        Objects.requireNonNull(balanceVersion, "balanceVersion");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(createdTime, "createdTime");
    }

    public static GrowthRecord from(long feedingRunId, long companionId, long pictureId,
                                    FeedingGrowth growth, NutritionMode nutritionMode,
                                    boolean contentUnderstood, String idempotencyKey,
                                    String correlationId, Instant createdTime) {
        Objects.requireNonNull(growth, "growth");
        return new GrowthRecord(null, feedingRunId, companionId, pictureId,
                growth.eventType(), growth.lifeExperienceDelta(), growth.traitDelta(),
                growth.skillExperienceDelta(), growth.companionAfter(), growth.reason(),
                nutritionMode, contentUnderstood, growth.balanceVersion(), idempotencyKey,
                correlationId, createdTime);
    }

    public GrowthRecord withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new GrowthRecord(persistedId, feedingRunId, companionId, pictureId, eventType,
                lifeExperienceDelta, traitDelta, skillExperienceDelta, companionAfter, reason,
                nutritionMode, contentUnderstood, balanceVersion, idempotencyKey,
                correlationId, createdTime);
    }
}
