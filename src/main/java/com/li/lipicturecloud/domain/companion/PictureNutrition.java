package com.li.lipicturecloud.domain.companion;

import java.util.Map;
import java.util.Objects;

public record PictureNutrition(
        long requestedLifeExperience,
        TraitDelta requestedTraitDelta,
        Map<CompanionSkill, Long> requestedSkillExperience,
        String reason,
        NutritionProvenance provenance,
        MoodImpact requestedMoodImpact,
        String memorySeed) {

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
        if (memorySeed != null) {
            // 记忆种子最终还会经过 CompanionMemory 的完整边界校验；这里先拒绝明显的脏输入。
            memorySeed = memorySeed.strip();
            if (memorySeed.isEmpty() || memorySeed.length() > CompanionMemory.MAX_CONTENT_CODE_POINTS * 3) {
                throw new IllegalArgumentException("memorySeed must be short plain text");
            }
        }
    }

    /**
     * 兼容构造器：无情绪影响、无记忆种子。旧的分析器迁移完成前保持可用。
     */
    public PictureNutrition(long requestedLifeExperience, TraitDelta requestedTraitDelta,
                            Map<CompanionSkill, Long> requestedSkillExperience, String reason,
                            NutritionProvenance provenance) {
        this(requestedLifeExperience, requestedTraitDelta, requestedSkillExperience, reason,
                provenance, null, null);
    }

    /**
     * 兼容构造器 for deterministic analyzers while their callers migrate to
     * {@link #provenance()}. It never fabricates visual content understanding.
     */
    public PictureNutrition(long requestedLifeExperience, TraitDelta requestedTraitDelta,
                            Map<CompanionSkill, Long> requestedSkillExperience, String reason,
                            NutritionMode nutritionMode, boolean contentUnderstood) {
        this(requestedLifeExperience, requestedTraitDelta, requestedSkillExperience, reason,
                legacyProvenance(nutritionMode, contentUnderstood), null, null);
    }

    public static PictureNutrition demo(long experience, TraitDelta traits,
                                        Map<CompanionSkill, Long> skills, String reason) {
        return new PictureNutrition(experience, traits, skills, reason,
                NutritionProvenance.demo(), null, null);
    }

    public static PictureNutrition fromObservation(long experience, TraitDelta traits,
                                                   Map<CompanionSkill, Long> skills, String reason) {
        return new PictureNutrition(experience, traits, skills, reason,
                NutritionProvenance.metadata(), null, null);
    }

    /** 是否携带了记忆候选种子（仅真实视觉理解与 Demo 测试档）。 */
    public boolean hasMemorySeed() {
        return memorySeed != null;
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
