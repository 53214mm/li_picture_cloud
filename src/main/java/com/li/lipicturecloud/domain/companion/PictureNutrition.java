package com.li.lipicturecloud.domain.companion;

import java.util.Map;
import java.util.Objects;

public record PictureNutrition(
        long requestedLifeExperience,
        TraitDelta requestedTraitDelta,
        Map<CompanionSkill, Long> requestedSkillExperience,
        String reason) {

    public PictureNutrition {
        if (requestedLifeExperience < 0) {
            throw new IllegalArgumentException("experience must be nonnegative");
        }
        Objects.requireNonNull(requestedTraitDelta, "requestedTraitDelta");
        Objects.requireNonNull(requestedSkillExperience, "requestedSkillExperience");
        Objects.requireNonNull(reason, "reason");
        if (requestedSkillExperience.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0)) {
            throw new IllegalArgumentException("skill experience must be nonnegative");
        }
        requestedSkillExperience = Map.copyOf(requestedSkillExperience);
    }

    public static PictureNutrition demo(long experience, TraitDelta traits,
                                        Map<CompanionSkill, Long> skills, String reason) {
        return new PictureNutrition(experience, traits, skills, reason);
    }
}
