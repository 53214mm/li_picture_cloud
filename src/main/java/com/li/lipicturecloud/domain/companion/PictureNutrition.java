package com.li.lipicturecloud.domain.companion;

import java.util.Map;
import java.util.Objects;

public record PictureNutrition(
        long requestedLifeExperience,
        TraitDelta requestedTraitDelta,
        Map<CompanionSkill, Long> requestedSkillExperience,
        String reason,
        NutritionMode nutritionMode,
        boolean contentUnderstood) {

    public PictureNutrition {
        if (requestedLifeExperience < 0) {
            throw new IllegalArgumentException("experience must be nonnegative");
        }
        Objects.requireNonNull(requestedTraitDelta, "requestedTraitDelta");
        Objects.requireNonNull(requestedSkillExperience, "requestedSkillExperience");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(nutritionMode, "nutritionMode");
        if (requestedSkillExperience.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0)) {
            throw new IllegalArgumentException("skill experience must be nonnegative");
        }
        requestedSkillExperience = Map.copyOf(requestedSkillExperience);
    }

    public static PictureNutrition demo(long experience, TraitDelta traits,
                                        Map<CompanionSkill, Long> skills, String reason) {
        return new PictureNutrition(experience, traits, skills, reason,
                NutritionMode.DEMO_DETERMINISTIC, false);
    }

    public static PictureNutrition fromObservation(long experience, TraitDelta traits,
                                                   Map<CompanionSkill, Long> skills, String reason) {
        return new PictureNutrition(experience, traits, skills, reason,
                NutritionMode.METADATA_DETERMINISTIC, false);
    }
}
