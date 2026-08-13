package com.li.lipicturecloud.infrastructure.companion;

import com.li.lipicturecloud.application.companion.AuthorizedPictureRef;
import com.li.lipicturecloud.application.companion.PictureObservation;
import com.li.lipicturecloud.application.companion.PictureObservationProvider;
import com.li.lipicturecloud.domain.companion.CompanionSkill;
import com.li.lipicturecloud.domain.companion.NutritionMode;
import com.li.lipicturecloud.domain.companion.PictureNutrition;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetadataPictureNutritionAdapterTest {

    @Test
    void completeMetadataProducesDeterministicBoundedNutritionWithoutClaimingPixelUnderstanding() {
        PictureObservationProvider provider = mock(PictureObservationProvider.class);
        AuthorizedPictureRef picture = new AuthorizedPictureRef(AuthorizationSubject.user(7L), 102L);
        when(provider.observe(picture)).thenReturn(new PictureObservation(
                102L, true, true, 1920, 1080, 2_048_000L, "jpeg"));
        MetadataPictureNutritionAdapter adapter = new MetadataPictureNutritionAdapter(provider);

        PictureNutrition first = adapter.analyze(picture);
        PictureNutrition second = adapter.analyze(picture);

        assertThat(second).isEqualTo(first);
        assertThat(adapter.mode()).isEqualTo(NutritionMode.METADATA_DETERMINISTIC);
        assertThat(adapter.contentUnderstood()).isFalse();
        assertThat(first.nutritionMode()).isEqualTo(NutritionMode.METADATA_DETERMINISTIC);
        assertThat(first.contentUnderstood()).isFalse();
        assertThat(first.requestedLifeExperience()).isEqualTo(40L);
        assertThat(first.requestedTraitDelta().curiosity()).isEqualByComparingTo(new BigDecimal("0.25"));
        assertThat(first.requestedTraitDelta().creativity()).isEqualByComparingTo(new BigDecimal("0.35"));
        assertThat(first.requestedSkillExperience())
                .containsEntry(CompanionSkill.IMAGE_OBSERVATION, 23L)
                .containsEntry(CompanionSkill.STORY_CREATION, 5L)
                .containsEntry(CompanionSkill.GALLERY_SEARCH, 4L);
        assertThat(first.reason()).contains("元数据", "未分析图片像素");
    }

    @Test
    void sparseMetadataStillProvidesSmallExplainableNutrition() {
        PictureObservationProvider provider = ignored ->
                new PictureObservation(102L, false, false, null, null, null, null);
        MetadataPictureNutritionAdapter adapter = new MetadataPictureNutritionAdapter(provider);

        PictureNutrition nutrition = adapter.analyze(
                new AuthorizedPictureRef(AuthorizationSubject.user(7L), 102L));

        assertThat(nutrition.requestedLifeExperience()).isEqualTo(25L);
        assertThat(nutrition.requestedSkillExperience())
                .containsOnlyKeys(CompanionSkill.IMAGE_OBSERVATION)
                .containsEntry(CompanionSkill.IMAGE_OBSERVATION, 8L);
    }

    @Test
    void rejectsObservationForAResourceOtherThanTheAuthorizedPicture() {
        PictureObservationProvider provider = ignored ->
                new PictureObservation(999L, false, false, null, null, null, null);
        MetadataPictureNutritionAdapter adapter = new MetadataPictureNutritionAdapter(provider);

        assertThatThrownBy(() -> adapter.analyze(
                new AuthorizedPictureRef(AuthorizationSubject.user(7L), 102L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("授权图片不一致");
    }
}
