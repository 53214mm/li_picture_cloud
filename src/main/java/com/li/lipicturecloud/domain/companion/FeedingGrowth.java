package com.li.lipicturecloud.domain.companion;

import java.util.Map;
import java.util.Objects;

/**
 * {@link Companion#feed(PictureNutrition, FeedingContext, CompanionBalance)} 的纯领域计算结果。
 *
 * <p>它同时携带新的伙伴快照和本次实际增量，随后由应用层原子保存并生成成长记录。</p>
 */
public record FeedingGrowth(
        Companion companionAfter,
        GrowthEventType eventType,
        long lifeExperienceDelta,
        TraitDelta traitDelta,
        Map<CompanionSkill, Long> skillExperienceDelta,
        String reason,
        String balanceVersion) {

    public FeedingGrowth {
        Objects.requireNonNull(companionAfter, "companionAfter");
        Objects.requireNonNull(eventType, "eventType");
        if (lifeExperienceDelta < 0) {
            throw new IllegalArgumentException("life experience delta must be nonnegative");
        }
        Objects.requireNonNull(traitDelta, "traitDelta");
        Objects.requireNonNull(skillExperienceDelta, "skillExperienceDelta");
        if (skillExperienceDelta.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0)) {
            throw new IllegalArgumentException("skill experience delta must be nonnegative");
        }
        skillExperienceDelta = Map.copyOf(skillExperienceDelta);
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(balanceVersion, "balanceVersion");
    }
}
