package com.li.lipicturecloud.domain.companion;

import java.util.Map;
import java.util.Objects;

public record PictureNutrition(
        long requestedLifeExperience,
        TraitDelta requestedTraitDelta,
        Map<CompanionSkill, Long> requestedSkillExperience,
        String reason,
        NutritionProvenance provenance) {

    public PictureNutrition {
        if (requestedLifeExperience < 0) {
            throw new IllegalArgumentException("experience must be nonnegative");
        }
        Objects.requireNonNull(requestedTraitDelta, "requestedTraitDelta");
        Objects.requireNonNull(requestedSkillExperience, "requestedSkillExperience");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(provenance, "provenance");
        if (requestedSkillExperience.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0)) {
            throw new IllegalArgumentException("skill experience must be nonnegative");
        }
        requestedSkillExperience = Map.copyOf(requestedSkillExperience);
    }

    /**
     * Compatibility constructor for deterministic analyzers while their callers migrate to
     * {@link #provenance()}. It never fabricates visual content understanding.
     */
    public PictureNutrition(long requestedLifeExperience, TraitDelta requestedTraitDelta,
                            Map<CompanionSkill, Long> requestedSkillExperience, String reason,
                            NutritionMode nutritionMode, boolean contentUnderstood) {
        this(requestedLifeExperience, requestedTraitDelta, requestedSkillExperience, reason,
                legacyProvenance(nutritionMode, contentUnderstood));
    }

    public static PictureNutrition demo(long experience, TraitDelta traits,
                                        Map<CompanionSkill, Long> skills, String reason) {
        return new PictureNutrition(experience, traits, skills, reason,
                NutritionProvenance.demo());
    }

    public static PictureNutrition fromObservation(long experience, TraitDelta traits,
                                                   Map<CompanionSkill, Long> skills, String reason) {
        return new PictureNutrition(experience, traits, skills, reason,
                NutritionProvenance.metadata());
    }

    /** @deprecated Use {@link #provenance()} as the only source of actual analysis facts. */
    @Deprecated(forRemoval = false)
    public NutritionMode nutritionMode() {
        return provenance.actualMode();
    }

    /** @deprecated Use {@link #provenance()} as the only source of actual analysis facts. */
    @Deprecated(forRemoval = false)
    public boolean contentUnderstood() {
        return provenance.contentUnderstood();
    }

    private static NutritionProvenance legacyProvenance(NutritionMode mode, boolean contentUnderstood) {
        if (contentUnderstood) {
            throw new IllegalArgumentException("legacy constructor cannot claim content understanding");
        }
        return switch (Objects.requireNonNull(mode, "nutritionMode")) {
            case DEMO_DETERMINISTIC -> NutritionProvenance.demo();
            case METADATA_DETERMINISTIC -> NutritionProvenance.metadata();
            case VISUAL_MODEL -> throw new IllegalArgumentException("visual nutrition requires explicit provenance");
        };
    }
}
