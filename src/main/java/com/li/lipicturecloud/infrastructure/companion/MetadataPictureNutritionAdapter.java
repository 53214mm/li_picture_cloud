package com.li.lipicturecloud.infrastructure.companion;

import com.li.lipicturecloud.application.companion.AuthorizedPictureRef;
import com.li.lipicturecloud.application.companion.PictureNutritionAnalyzer;
import com.li.lipicturecloud.application.companion.PictureObservation;
import com.li.lipicturecloud.application.companion.PictureObservationProvider;
import com.li.lipicturecloud.domain.companion.CompanionSkill;
import com.li.lipicturecloud.domain.companion.NutritionMode;
import com.li.lipicturecloud.domain.companion.NutritionPolicy;
import com.li.lipicturecloud.domain.companion.PictureNutrition;
import com.li.lipicturecloud.domain.companion.TraitDelta;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * 将真实图片元数据转换成可解释、可复算的候选营养。
 *
 * <p>这里的数值仍是候选值，最终上限由 {@code CompanionBalance} 统一裁剪。
 * Provider 不读取像素，因此本实现始终如实返回 {@code contentUnderstood=false}。</p>
 */
public class MetadataPictureNutritionAdapter implements PictureNutritionAnalyzer {

    private final PictureObservationProvider observations;

    public MetadataPictureNutritionAdapter(PictureObservationProvider observations) {
        this.observations = observations;
    }

    @Override
    public NutritionPolicy policy() {
        return NutritionPolicy.METADATA_ONLY;
    }

    @Override
    public NutritionMode mode() {
        return NutritionMode.METADATA_DETERMINISTIC;
    }

    @Override
    public boolean contentUnderstood() {
        return false;
    }

    @Override
    public PictureNutrition analyze(AuthorizedPictureRef picture) {
        PictureObservation observation = observations.observe(picture);
        if (observation.pictureId() != picture.pictureId()) {
            throw new IllegalStateException("图片观察结果与授权图片不一致");
        }
        long experience = 25L;
        long observationSkill = 8L;
        if (observation.hasDimensions()) {
            experience += 5L;
            observationSkill += 7L;
        }
        if (observation.sizeBytes() != null) {
            experience += 3L;
            observationSkill += 3L;
        }
        if (observation.format() != null) {
            experience += 2L;
            observationSkill += 2L;
        }
        if (observation.hasDescription()) {
            experience += 3L;
            observationSkill += 2L;
        }
        if (observation.hasCategory()) {
            experience += 2L;
            observationSkill += 1L;
        }

        Map<CompanionSkill, Long> skills = new EnumMap<>(CompanionSkill.class);
        skills.put(CompanionSkill.IMAGE_OBSERVATION, observationSkill);
        if (observation.hasDescription()) {
            skills.put(CompanionSkill.STORY_CREATION, 5L);
        }
        if (observation.hasCategory()) {
            skills.put(CompanionSkill.GALLERY_SEARCH, 4L);
        }
        TraitDelta traits = new TraitDelta(
                decimal(observation.hasDimensions() || observation.format() != null ? "0.25" : "0.10"),
                decimal("0.10"), decimal("0.00"),
                decimal(observation.hasDescription() ? "0.15" : "0.00"),
                decimal(observation.hasDescription() || observation.hasCategory() ? "0.35" : "0.05"));
        return PictureNutrition.fromObservation(experience, traits, Map.copyOf(skills),
                "伙伴从图片元数据中获得了基础营养；未分析图片像素或理解具体内容。");
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
