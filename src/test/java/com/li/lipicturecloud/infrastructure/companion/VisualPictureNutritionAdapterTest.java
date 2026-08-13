package com.li.lipicturecloud.infrastructure.companion;

import com.li.lipicturecloud.application.companion.AuthorizedPictureContent;
import com.li.lipicturecloud.application.companion.AuthorizedPictureContentProvider;
import com.li.lipicturecloud.application.companion.AuthorizedPictureRef;
import com.li.lipicturecloud.application.companion.VisualObservationCandidate;
import com.li.lipicturecloud.application.companion.VisualObservationProvider;
import com.li.lipicturecloud.application.companion.VisionContentException;
import com.li.lipicturecloud.application.companion.VisionProviderException;
import com.li.lipicturecloud.application.companion.VisionQuotaGuard;
import com.li.lipicturecloud.domain.companion.CompanionSkill;
import com.li.lipicturecloud.domain.companion.NutritionMode;
import com.li.lipicturecloud.domain.companion.PictureNutrition;
import com.li.lipicturecloud.domain.companion.TraitDelta;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 锁定视觉候选到伙伴营养的映射，以及哪些失败可以诚实降级为元数据营养。
 */
class VisualPictureNutritionAdapterTest {

    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final int DAILY_LIMIT = 10;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-13T16:00:00Z"), ZoneOffset.UTC);

    @Test
    void convertsStrictVisualCandidateIntoDeterministicNutritionAndActualProvenance() {
        VisionQuotaGuard quota = mock(VisionQuotaGuard.class);
        AuthorizedPictureContentProvider contents = mock(AuthorizedPictureContentProvider.class);
        VisualObservationProvider visual = mock(VisualObservationProvider.class);
        MetadataPictureNutritionAdapter metadata = mock(MetadataPictureNutritionAdapter.class);
        AuthorizedPictureRef picture = picture();
        AuthorizedPictureContent content = jpegContent();
        when(contents.load(picture, MAX_BYTES)).thenReturn(content);
        when(visual.observe(content)).thenReturn(new VisualObservationCandidate(
                VisualObservationCandidate.Mood.JOYFUL, 3, 3, true, 2, 4, new BigDecimal("0.84")));
        when(visual.providerCode()).thenReturn("dashscope");
        when(visual.modelCode()).thenReturn("qwen3.6-flash");
        when(visual.promptVersion()).thenReturn("companion-vision-v1");
        when(visual.resultSchemaVersion()).thenReturn("visual-observation-v1");

        PictureNutrition nutrition = new VisualPictureNutritionAdapter(
                quota, contents, visual, metadata, CLOCK, MAX_BYTES, DAILY_LIMIT).analyze(picture);

        assertThat(nutrition.requestedLifeExperience()).isEqualTo(48L);
        assertThat(nutrition.requestedTraitDelta().curiosity()).isEqualByComparingTo("0.50");
        assertThat(nutrition.requestedTraitDelta().enthusiasm()).isEqualByComparingTo("0.30");
        assertThat(nutrition.requestedTraitDelta().playfulness()).isEqualByComparingTo("0.30");
        assertThat(nutrition.requestedTraitDelta().empathy()).isEqualByComparingTo("0.20");
        assertThat(nutrition.requestedTraitDelta().creativity()).isEqualByComparingTo("0.40");
        assertThat(nutrition.requestedSkillExperience()).containsExactlyInAnyOrderEntriesOf(Map.of(
                CompanionSkill.IMAGE_OBSERVATION, 27L,
                CompanionSkill.STORY_CREATION, 14L,
                CompanionSkill.EMOJI_CREATION, 7L));
        assertThat(nutrition.provenance().actualMode()).isEqualTo(NutritionMode.VISUAL_MODEL);
        assertThat(nutrition.provenance().contentUnderstood()).isTrue();
        assertThat(nutrition.provenance().providerCode()).isEqualTo("dashscope");
        assertThat(nutrition.provenance().modelCode()).isEqualTo("qwen3.6-flash");
        assertThat(nutrition.provenance().confidence()).isEqualByComparingTo("0.84");

        InOrder order = inOrder(contents, quota, visual);
        order.verify(contents).load(picture, MAX_BYTES);
        order.verify(quota).reserve(7L, LocalDate.of(2026, 8, 14), DAILY_LIMIT);
        order.verify(contents).verifyStillAuthorized(picture, content);
        order.verify(visual).observe(content);
        verify(metadata, never()).analyze(any());
    }

    @Test
    void downgradesOnlyAnApprovedVisionFailureToExplicitMetadataProvenance() {
        VisionQuotaGuard quota = mock(VisionQuotaGuard.class);
        AuthorizedPictureContentProvider contents = mock(AuthorizedPictureContentProvider.class);
        VisualObservationProvider visual = mock(VisualObservationProvider.class);
        MetadataPictureNutritionAdapter metadata = mock(MetadataPictureNutritionAdapter.class);
        AuthorizedPictureRef picture = picture();
        AuthorizedPictureContent content = jpegContent();
        PictureNutrition metadataNutrition = PictureNutrition.fromObservation(31L, TraitDelta.zero(),
                Map.of(CompanionSkill.IMAGE_OBSERVATION, 8L), "未读取图片像素");
        when(contents.load(picture, MAX_BYTES)).thenReturn(content);
        when(visual.observe(content)).thenThrow(new VisionProviderException("VISION_TIMEOUT", "视觉服务暂不可用"));
        when(metadata.analyze(picture)).thenReturn(metadataNutrition);

        PictureNutrition nutrition = new VisualPictureNutritionAdapter(
                quota, contents, visual, metadata, CLOCK, MAX_BYTES, DAILY_LIMIT).analyze(picture);

        assertThat(nutrition.requestedLifeExperience()).isEqualTo(31L);
        assertThat(nutrition.provenance().actualMode()).isEqualTo(NutritionMode.METADATA_DETERMINISTIC);
        assertThat(nutrition.provenance().contentUnderstood()).isFalse();
        assertThat(nutrition.provenance().fallbackReasonCode()).isEqualTo("VISION_TIMEOUT");
        verify(quota).reserve(7L, LocalDate.of(2026, 8, 14), DAILY_LIMIT);
        verify(metadata).analyze(picture);
    }

    @Test
    void credentialsFailureIsNotHiddenByMetadataFallback() {
        VisionQuotaGuard quota = mock(VisionQuotaGuard.class);
        AuthorizedPictureContentProvider contents = mock(AuthorizedPictureContentProvider.class);
        VisualObservationProvider visual = mock(VisualObservationProvider.class);
        MetadataPictureNutritionAdapter metadata = mock(MetadataPictureNutritionAdapter.class);
        AuthorizedPictureRef picture = picture();
        AuthorizedPictureContent content = jpegContent();
        VisionProviderException credentials = new VisionProviderException("VISION_CREDENTIALS", "视觉服务暂不可用");
        when(contents.load(picture, MAX_BYTES)).thenReturn(content);
        when(visual.observe(content)).thenThrow(credentials);

        assertThatThrownBy(() -> new VisualPictureNutritionAdapter(
                quota, contents, visual, metadata, CLOCK, MAX_BYTES, DAILY_LIMIT).analyze(picture))
                .isSameAs(credentials);

        verify(quota).reserve(7L, LocalDate.of(2026, 8, 14), DAILY_LIMIT);
        verify(metadata, never()).analyze(any());
    }

    @Test
    void contentSizeLimitFallsBackWithoutReservingQuotaBecauseNoPixelsLeaveTheService() {
        VisionQuotaGuard quota = mock(VisionQuotaGuard.class);
        AuthorizedPictureContentProvider contents = mock(AuthorizedPictureContentProvider.class);
        VisualObservationProvider visual = mock(VisualObservationProvider.class);
        MetadataPictureNutritionAdapter metadata = mock(MetadataPictureNutritionAdapter.class);
        AuthorizedPictureRef picture = picture();
        when(contents.load(picture, MAX_BYTES))
                .thenThrow(new VisionContentException("VISION_IMAGE_TOO_LARGE", "图片内容超过视觉营养大小上限"));
        when(metadata.analyze(picture)).thenReturn(PictureNutrition.fromObservation(25L, TraitDelta.zero(),
                Map.of(CompanionSkill.IMAGE_OBSERVATION, 8L), "未读取图片像素"));

        PictureNutrition nutrition = new VisualPictureNutritionAdapter(
                quota, contents, visual, metadata, CLOCK, MAX_BYTES, DAILY_LIMIT).analyze(picture);

        assertThat(nutrition.provenance().fallbackReasonCode()).isEqualTo("VISION_IMAGE_TOO_LARGE");
        verify(quota, never()).reserve(any(Long.class), any(LocalDate.class), any(Integer.class));
        verify(visual, never()).observe(any());
    }

    @Test
    void familiarPictureUsesExplicitMetadataSourceWithoutReadingPixelsOrSpendingQuota() {
        VisionQuotaGuard quota = mock(VisionQuotaGuard.class);
        AuthorizedPictureContentProvider contents = mock(AuthorizedPictureContentProvider.class);
        VisualObservationProvider visual = mock(VisualObservationProvider.class);
        MetadataPictureNutritionAdapter metadata = mock(MetadataPictureNutritionAdapter.class);
        AuthorizedPictureRef picture = picture();
        when(metadata.analyze(picture)).thenReturn(PictureNutrition.fromObservation(25L, TraitDelta.zero(),
                Map.of(CompanionSkill.IMAGE_OBSERVATION, 8L), "未读取图片像素"));

        PictureNutrition nutrition = new VisualPictureNutritionAdapter(
                quota, contents, visual, metadata, CLOCK, MAX_BYTES, DAILY_LIMIT).analyzeFamiliar(picture);

        assertThat(nutrition.provenance().actualMode()).isEqualTo(NutritionMode.METADATA_DETERMINISTIC);
        assertThat(nutrition.provenance().fallbackReasonCode()).isEqualTo("SKIPPED_FAMILIAR");
        verify(contents, never()).load(any(), any(Long.class));
        verify(quota, never()).reserve(any(Long.class), any(LocalDate.class), any(Integer.class));
        verify(visual, never()).observe(any());
    }

    private static AuthorizedPictureRef picture() {
        return new AuthorizedPictureRef(AuthorizationSubject.user(7L), 102L);
    }

    private static AuthorizedPictureContent jpegContent() {
        return new AuthorizedPictureContent(102L, Instant.parse("2026-08-13T16:00:00Z"),
                "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
    }
}
