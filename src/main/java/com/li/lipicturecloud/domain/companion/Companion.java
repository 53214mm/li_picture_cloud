package com.li.lipicturecloud.domain.companion;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

/**
 * 伙伴聚合根：只保存长期、可审计的成长状态。
 *
 * <p>它不认识 HTTP、数据库或图片表；应用层先确认“谁能看这张图”，再把经过分析的
 * {@link PictureNutrition} 交给本对象应用规则。</p>
 */
public record Companion(
        Long id,
        long ownerId,
        long lifeExperience,
        int level,
        CompanionStage lifeStage,
        CompanionTraits traits,
        Map<CompanionSkill, Long> skillExperience,
        String balanceVersion,
        long revision) {

    public Companion {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (ownerId <= 0 || lifeExperience < 0 || level < 1 || revision < 0) {
            throw new IllegalArgumentException("invalid companion state");
        }
        Objects.requireNonNull(lifeStage, "lifeStage");
        Objects.requireNonNull(traits, "traits");
        Objects.requireNonNull(balanceVersion, "balanceVersion");
        // 恢复数据时同时校验派生字段，避免 XP、等级、阶段互相矛盾的脏数据继续传播。
        CompanionBalance supportedBalance = CompanionBalance.v1();
        if (!supportedBalance.version().equals(balanceVersion)
                || level != supportedBalance.levelFor(lifeExperience)
                || lifeStage != supportedBalance.stageFor(level)) {
            throw new IllegalArgumentException("unsupported or inconsistent balance state");
        }
        skillExperience = completeSkills(skillExperience);
    }

    public static Companion awaken(long ownerId, CompanionBalance balance) {
        if (ownerId <= 0) {
            throw new IllegalArgumentException("ownerId must be positive");
        }
        CompanionBalance checkedBalance = requireSupportedBalance(balance);
        return new Companion(null, ownerId, 0L, 1, CompanionStage.LIGHT,
                CompanionTraits.neutral(), zeroSkills(), checkedBalance.version(), 0L);
    }

    public static Companion restore(Long id, long ownerId, long lifeExperience, int level,
                                    CompanionStage lifeStage, CompanionTraits traits,
                                    Map<CompanionSkill, Long> skillExperience,
                                    String balanceVersion, long revision,
                                    CompanionBalance balance) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("persisted id must be positive");
        }
        CompanionBalance checkedBalance = requireSupportedBalance(balance);
        if (!checkedBalance.version().equals(balanceVersion)
                || level != checkedBalance.levelFor(lifeExperience)
                || lifeStage != checkedBalance.stageFor(level)) {
            throw new IllegalArgumentException("unsupported or inconsistent balance state");
        }
        return new Companion(id, ownerId, lifeExperience, level, lifeStage, traits,
                skillExperience, balanceVersion, revision);
    }

    public Companion persistedAs(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new Companion(persistedId, ownerId, lifeExperience, level, lifeStage,
                traits, skillExperience, balanceVersion, revision);
    }

    public FeedingGrowth feed(PictureNutrition nutrition, FeedingContext context,
                              CompanionBalance balance) {
        Objects.requireNonNull(nutrition, "nutrition");
        Objects.requireNonNull(context, "context");
        CompanionBalance checkedBalance = requireMatchingBalance(balance);
        // 同一图片第二次及以后只提供“熟悉感”，不能重复刷性格和技能。
        GrowthEventType event = context.picturePreviouslyFed()
                ? GrowthEventType.PICTURE_REVISITED : GrowthEventType.PICTURE_FED;
        long experienceDelta = context.picturePreviouslyFed()
                ? checkedBalance.revisitExperience(context.lifeExperienceEarnedToday(),
                context.revisitExperienceEarnedForPicture())
                : checkedBalance.fullFeedExperience(nutrition.requestedLifeExperience(),
                context.lifeExperienceEarnedToday());
        TraitDelta traitDelta = context.picturePreviouslyFed()
                ? TraitDelta.zero() : applyTraits(nutrition.requestedTraitDelta(), checkedBalance);
        Map<CompanionSkill, Long> skillDelta = context.picturePreviouslyFed()
                ? Map.of() : applySkillCaps(nutrition.requestedSkillExperience(), checkedBalance);
        // 先算出完整 after 快照，再由仓储用 revision 做一次 CAS 写入，保证成长原子化。
        Companion after = grow(experienceDelta, traitDelta, skillDelta, checkedBalance);
        String reason = context.picturePreviouslyFed()
                ? "它认出了曾经品尝过的图片，只留下了一点熟悉感。"
                : nutrition.reason();
        return new FeedingGrowth(after, event, experienceDelta, traitDelta,
                skillDelta, reason, checkedBalance.version());
    }

    private Companion grow(long experienceDelta, TraitDelta traitDelta,
                           Map<CompanionSkill, Long> skillDelta, CompanionBalance balance) {
        long afterExperience = Math.addExact(lifeExperience, experienceDelta);
        CompanionTraits afterTraits = new CompanionTraits(
                traits.curiosity().add(traitDelta.curiosity()),
                traits.enthusiasm().add(traitDelta.enthusiasm()),
                traits.playfulness().add(traitDelta.playfulness()),
                traits.empathy().add(traitDelta.empathy()),
                traits.creativity().add(traitDelta.creativity()));
        Map<CompanionSkill, Long> afterSkills = new EnumMap<>(CompanionSkill.class);
        for (CompanionSkill skill : CompanionSkill.values()) {
            afterSkills.put(skill, Math.addExact(skillExperience.get(skill), skillDelta.getOrDefault(skill, 0L)));
        }
        int afterLevel = balance.levelFor(afterExperience);
        return new Companion(id, ownerId, afterExperience, afterLevel, balance.stageFor(afterLevel),
                afterTraits, afterSkills, balanceVersion, Math.addExact(revision, 1L));
    }

    private TraitDelta applyTraits(TraitDelta requested, CompanionBalance balance) {
        return new TraitDelta(
                balance.applyTrait(traits.curiosity(), requested.curiosity()),
                balance.applyTrait(traits.enthusiasm(), requested.enthusiasm()),
                balance.applyTrait(traits.playfulness(), requested.playfulness()),
                balance.applyTrait(traits.empathy(), requested.empathy()),
                balance.applyTrait(traits.creativity(), requested.creativity()));
    }

    private static Map<CompanionSkill, Long> applySkillCaps(
            Map<CompanionSkill, Long> requested, CompanionBalance balance) {
        Map<CompanionSkill, Long> result = new EnumMap<>(CompanionSkill.class);
        for (Map.Entry<CompanionSkill, Long> entry : requested.entrySet()) {
            long capped = balance.skillExperience(entry.getValue());
            if (capped > 0) {
                result.put(entry.getKey(), capped);
            }
        }
        return Map.copyOf(result);
    }

    private CompanionBalance requireMatchingBalance(CompanionBalance balance) {
        CompanionBalance checkedBalance = requireSupportedBalance(balance);
        if (!balanceVersion.equals(checkedBalance.version())) {
            throw new IllegalArgumentException("balance version does not match companion");
        }
        return checkedBalance;
    }

    private static CompanionBalance requireSupportedBalance(CompanionBalance balance) {
        CompanionBalance checkedBalance = Objects.requireNonNull(balance, "balance");
        if (!CompanionBalance.v1().version().equals(checkedBalance.version())) {
            throw new IllegalArgumentException("unsupported balance");
        }
        return checkedBalance;
    }

    private static Map<CompanionSkill, Long> zeroSkills() {
        Map<CompanionSkill, Long> result = new EnumMap<>(CompanionSkill.class);
        for (CompanionSkill skill : CompanionSkill.values()) {
            result.put(skill, 0L);
        }
        return result;
    }

    private static Map<CompanionSkill, Long> completeSkills(Map<CompanionSkill, Long> skills) {
        Objects.requireNonNull(skills, "skillExperience");
        if (!skills.keySet().equals(EnumSet.allOf(CompanionSkill.class))
                || skills.values().stream().anyMatch(value -> value == null || value < 0)) {
            throw new IllegalArgumentException("skill map must be complete and nonnegative");
        }
        return Map.copyOf(skills);
    }
}
