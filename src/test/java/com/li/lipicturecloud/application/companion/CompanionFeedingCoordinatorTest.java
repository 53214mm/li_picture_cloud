package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.config.CompanionFeatureProperties;
import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.domain.companion.CompanionMemory;
import com.li.lipicturecloud.domain.companion.CompanionMemoryRepository;
import com.li.lipicturecloud.domain.companion.CompanionMood;
import com.li.lipicturecloud.domain.companion.CompanionMoodRepository;
import com.li.lipicturecloud.domain.companion.CompanionMoodRules;
import com.li.lipicturecloud.domain.companion.CompanionRelationship;
import com.li.lipicturecloud.domain.companion.CompanionRelationshipRepository;
import com.li.lipicturecloud.domain.companion.CompanionRelationshipRules;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
import com.li.lipicturecloud.domain.companion.CompanionSkill;
import com.li.lipicturecloud.domain.companion.FeedingRun;
import com.li.lipicturecloud.domain.companion.FeedingRunRepository;
import com.li.lipicturecloud.domain.companion.GrowthRecord;
import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import com.li.lipicturecloud.domain.companion.MemoryStatus;
import com.li.lipicturecloud.domain.companion.MoodImpact;
import com.li.lipicturecloud.domain.companion.NutritionMode;
import com.li.lipicturecloud.domain.companion.NutritionPolicy;
import com.li.lipicturecloud.domain.companion.NutritionProvenance;
import com.li.lipicturecloud.domain.companion.PictureNutrition;
import com.li.lipicturecloud.domain.companion.TraitDelta;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanionFeedingCoordinatorTest {

    private static final Instant NOW = Instant.parse("2026-08-11T08:00:00Z");
    private static final String KEY = "6f26d166-0a82-4d9f-8a61-6c21cf2e59d0";
    private static final String CORRELATION = "fef53056-2d9f-467d-9b1d-1afe9a6638fe";

    private CompanionRepository companionRepository;
    private GrowthRecordRepository growthRepository;
    private FeedingRunRepository runRepository;
    private CompanionMoodRepository moodRepository;
    private CompanionRelationshipRepository relationshipRepository;
    private CompanionMemoryRepository memoryRepository;
    private CompanionFeedingCoordinator coordinator;

    @BeforeEach
    void setUp() {
        companionRepository = mock(CompanionRepository.class);
        growthRepository = mock(GrowthRecordRepository.class);
        runRepository = mock(FeedingRunRepository.class);
        moodRepository = mock(CompanionMoodRepository.class);
        relationshipRepository = mock(CompanionRelationshipRepository.class);
        memoryRepository = mock(CompanionMemoryRepository.class);
        // complete() 现在会在同一事务内结算情绪、关系与记忆；默认 stub 让旧用例专注于喂养本身。
        when(relationshipRepository.createIfAbsent(anyLong(), anyLong()))
                .thenAnswer(invocation -> CompanionRelationship.initial(
                        invocation.getArgument(0), invocation.getArgument(1)));
        when(relationshipRepository.save(any(), anyLong())).thenReturn(true);
        when(moodRepository.save(any(), anyLong())).thenReturn(true);
        when(moodRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(memoryRepository.append(any())).thenAnswer(invocation ->
                invocation.<CompanionMemory>getArgument(0).withId(41L));
        PictureNutritionAnalyzer analyzer = mock(PictureNutritionAnalyzer.class);
        CompanionBalance balance = CompanionBalance.v1();
        CompanionViewAssembler assembler = new CompanionViewAssembler(balance, analyzer,
                new CompanionFeatureProperties());
        coordinator = new CompanionFeedingCoordinator(companionRepository, growthRepository, runRepository,
                moodRepository, relationshipRepository, memoryRepository, balance,
                CompanionMoodRules.v1(), CompanionRelationshipRules.v1(), assembler,
                Clock.fixed(NOW, ZoneOffset.UTC), new CompanionFeatureProperties());
    }

    @Test
    void sameKeyWithDifferentPictureIsAConflict() {
        Companion companion = persistedCompanion();
        FeedingRun existing = processingRun(companion, 101L);
        when(runRepository.findByKey(companion.id(), KEY)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> coordinator.reserve(companion, AuthorizationSubject.user(7L), 102L,
                KEY, fingerprint(102L), CORRELATION, NutritionMode.DEMO_DETERMINISTIC, false))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode(), Throwable::getMessage)
                .containsExactly(ErrorCode.PARAMS_ERROR.getCode(), "幂等键已用于另一张图片");
    }

    @Test
    void completionUsesDailyAndRepeatFactsThenCommitsOneGrowthRecord() {
        Companion companion = persistedCompanion();
        FeedingRun run = processingRun(companion, 102L);
        when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));
        when(growthRepository.hasFullFeed(companion.id(), 102L)).thenReturn(true);
        when(growthRepository.sumLifeExperienceSince(eq(companion.id()), any())).thenReturn(20L);
        when(growthRepository.sumRevisitExperience(companion.id(), 102L)).thenReturn(1L);
        when(companionRepository.save(any(), eq(companion.revision()))).thenReturn(true);
        when(growthRepository.append(any())).thenAnswer(invocation ->
                invocation.<GrowthRecord>getArgument(0).withId(31L));
        when(runRepository.complete(run.id(), run.revision(), 31L, NOW)).thenReturn(true);

        var result = coordinator.complete(run, PictureNutrition.demo(42L, TraitDelta.zero(),
                Map.of(CompanionSkill.STORY_CREATION, 12L), "演示营养"));

        assertThat(result.outcome()).isEqualTo("FAMILIARITY");
        assertThat(result.growth().lifeExperienceDelta()).isEqualTo(1L);
        assertThat(result.growth().skillExperienceDelta()).isEmpty();
        verify(runRepository).complete(run.id(), run.revision(), 31L, NOW);
    }

    @Test
    void failedCompanionCompareAndSetStopsBeforeGrowthAppend() {
        Companion companion = persistedCompanion();
        FeedingRun run = processingRun(companion, 102L);
        when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));
        when(companionRepository.save(any(), eq(companion.revision()))).thenReturn(false);

        assertThatThrownBy(() -> coordinator.complete(run,
                PictureNutrition.demo(42L, TraitDelta.zero(), Map.of(), "演示营养")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("伙伴状态已变化，请重试");
        verify(growthRepository, never()).append(any());
    }

    @Test
    void actualAnalysisProvenanceMustBeAllowedByTheRequestedPolicy() {
        Companion companion = persistedCompanion();
        FeedingRun run = processingRun(companion, 102L);
        PictureNutrition metadata = PictureNutrition.fromObservation(
                40L, TraitDelta.zero(), Map.of(), "元数据营养");

        assertThatThrownBy(() -> coordinator.complete(run, metadata))
                .isInstanceOf(BusinessException.class)
                .hasMessage("图片营养实际来源不符合喂养策略");

        verify(companionRepository, never()).findByOwnerIdForUpdate(any(Long.class));
        verify(growthRepository, never()).append(any());
    }

    @Test
    void visualCompletionMustMatchTheProviderAndModelRequestedByTheRun() {
        Companion companion = persistedCompanion();
        FeedingRun run = FeedingRun.processing(companion.id(), 7L, 102L, KEY, fingerprint(102L), CORRELATION,
                NutritionPolicy.VISUAL_WITH_METADATA_FALLBACK, "dashscope", "qwen3.6-flash", NOW)
                .persistedAs(21L);
        PictureNutrition differentModel = new PictureNutrition(42L, TraitDelta.zero(), Map.of(), "视觉营养",
                NutritionProvenance.visual("dashscope", "another-model", "companion-vision-v1",
                        "visual-observation-v1", new BigDecimal("0.82")));

        assertThatThrownBy(() -> coordinator.complete(run, differentModel))
                .isInstanceOf(BusinessException.class)
                .hasMessage("图片营养实际模型与喂养请求不一致");

        verify(companionRepository, never()).findByOwnerIdForUpdate(any(Long.class));
    }

    @Test
    void failedRunCannotRestartUnderADifferentAnalyzerMode() {
        Companion companion = persistedCompanion();
        FeedingRun failed = processingRun(companion, 102L)
                .failed("NUTRITION_FAILED", "分析失败", NOW.plusSeconds(1));
        when(runRepository.findByKey(companion.id(), KEY)).thenReturn(Optional.of(failed));

        assertThatThrownBy(() -> coordinator.reserve(companion, AuthorizationSubject.user(7L), 102L,
                KEY, fingerprint(102L), CORRELATION, NutritionMode.METADATA_DETERMINISTIC, false))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode(), Throwable::getMessage)
                .containsExactly(ErrorCode.PARAMS_ERROR.getCode(), "图片营养模式已变化，请重新发起喂养");

        verify(runRepository, never()).restart(any(Long.class), any(Long.class), any());
    }

    @Test
    void failedVisualRunCannotRestartWhenTheRequestedModelHasChanged() {
        Companion companion = persistedCompanion();
        FeedingRun failed = FeedingRun.processing(companion.id(), 7L, 102L, KEY, fingerprint(102L), CORRELATION,
                NutritionPolicy.VISUAL_WITH_METADATA_FALLBACK, "dashscope", "qwen3.6-flash", NOW)
                .persistedAs(21L).failed("VISION_TIMEOUT", "本次没有消化成功", NOW.plusSeconds(1));
        when(runRepository.findByKey(companion.id(), KEY)).thenReturn(Optional.of(failed));

        assertThatThrownBy(() -> coordinator.reserve(companion, AuthorizationSubject.user(7L), 102L,
                KEY, fingerprint(102L), CORRELATION, NutritionPolicy.VISUAL_WITH_METADATA_FALLBACK,
                "dashscope", "qwen-next"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode(), Throwable::getMessage)
                .containsExactly(ErrorCode.PARAMS_ERROR.getCode(), "图片营养模式已变化，请重新发起喂养");

        verify(runRepository, never()).restart(any(Long.class), any(Long.class), any());
    }

    @Test
    void visualFallbackCanCompleteAndPersistsItsActualMetadataProvenance() {
        Companion companion = persistedCompanion();
        FeedingRun run = FeedingRun.processing(companion.id(), 7L, 102L, KEY, fingerprint(102L), CORRELATION,
                NutritionPolicy.VISUAL_WITH_METADATA_FALLBACK, "dashscope", "qwen3.6-flash", NOW)
                .persistedAs(21L);
        PictureNutrition fallback = new PictureNutrition(31L, TraitDelta.zero(), Map.of(), "元数据降级",
                NutritionProvenance.metadataFallback("VISION_TIMEOUT"));
        when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));
        when(companionRepository.save(any(), eq(companion.revision()))).thenReturn(true);
        when(growthRepository.append(any())).thenAnswer(invocation ->
                invocation.<GrowthRecord>getArgument(0).withId(31L));
        when(runRepository.complete(run.id(), run.revision(), 31L, NOW)).thenReturn(true);

        coordinator.complete(run, fallback);

        verify(growthRepository).append(org.mockito.ArgumentMatchers.argThat(record ->
                record.provenance().actualMode() == NutritionMode.METADATA_DETERMINISTIC
                        && "VISION_TIMEOUT".equals(record.provenance().fallbackReasonCode())));
    }

    @Test
    void viewExposesAStableVisualLabelWithoutLeakingProviderSecrets() {
        CompanionFeatureProperties properties = new CompanionFeatureProperties();
        properties.setVisionProvider("dashscope");
        properties.setVisionModel("qwen3.6-flash");
        properties.setVisionDailyLimit(10);
        PictureNutritionAnalyzer analyzer = mock(PictureNutritionAnalyzer.class);
        CompanionViewAssembler views = new CompanionViewAssembler(CompanionBalance.v1(), analyzer, properties);
        Companion companion = persistedCompanion();
        GrowthRecord record = new GrowthRecord(31L, 41L, companion.id(), 102L,
                com.li.lipicturecloud.domain.companion.GrowthEventType.PICTURE_FED, 42L,
                TraitDelta.zero(), Map.of(), companion, "观察完成",
                NutritionProvenance.visual("dashscope", "qwen3.6-flash", "companion-vision-v1",
                        "visual-observation-v1", new BigDecimal("0.82")),
                "life-core-v1", KEY, CORRELATION, NOW);

        assertThat(views.growth(record))
                .extracting("nutritionLabel", "providerCode", "modelCode", "confidence", "fallbackReasonCode")
                .containsExactly("Qwen 视觉营养 · 已分析图片内容", "dashscope", "qwen3.6-flash",
                        new BigDecimal("0.82"), null);
    }

    @Test
    void viewExposesFallbackAsMetadataRatherThanVisualUnderstanding() {
        PictureNutritionAnalyzer analyzer = mock(PictureNutritionAnalyzer.class);
        CompanionViewAssembler views = new CompanionViewAssembler(CompanionBalance.v1(), analyzer,
                new CompanionFeatureProperties());
        Companion companion = persistedCompanion();
        GrowthRecord record = new GrowthRecord(31L, 41L, companion.id(), 102L,
                com.li.lipicturecloud.domain.companion.GrowthEventType.PICTURE_FED, 42L,
                TraitDelta.zero(), Map.of(), companion, "元数据观察",
                NutritionProvenance.metadataFallback("VISION_TIMEOUT"),
                "life-core-v1", KEY, CORRELATION, NOW);

        assertThat(views.growth(record))
                .extracting("nutritionLabel", "contentUnderstood", "fallbackReasonCode")
                .containsExactly("视觉服务暂不可用，本次使用图片元数据营养", false, "VISION_TIMEOUT");
    }

    @Test
    void completionReadsClockOnlyOnceForDailyBoundaryAndAuditTime() {
        Companion companion = persistedCompanion();
        FeedingRun run = processingRun(companion, 102L);
        Clock singleUseClock = mock(Clock.class);
        when(singleUseClock.instant()).thenReturn(NOW);
        PictureNutritionAnalyzer analyzer = mock(PictureNutritionAnalyzer.class);
        CompanionViewAssembler localAssembler = new CompanionViewAssembler(CompanionBalance.v1(), analyzer,
                new CompanionFeatureProperties());
        CompanionFeedingCoordinator local = new CompanionFeedingCoordinator(companionRepository,
                growthRepository, runRepository, moodRepository, relationshipRepository,
                memoryRepository, CompanionBalance.v1(), CompanionMoodRules.v1(),
                CompanionRelationshipRules.v1(), localAssembler,
                singleUseClock, new CompanionFeatureProperties());
        when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));
        when(companionRepository.save(any(), eq(0L))).thenReturn(true);
        when(growthRepository.append(any())).thenAnswer(invocation ->
                invocation.<GrowthRecord>getArgument(0).withId(31L));
        when(runRepository.complete(run.id(), run.revision(), 31L, NOW)).thenReturn(true);

        local.complete(run, PictureNutrition.demo(42L, TraitDelta.zero(), Map.of(), "演示营养"));

        verify(singleUseClock, times(1)).instant();
        verify(runRepository).complete(run.id(), run.revision(), 31L, NOW);
    }

    @Test
    void staleAttemptCannotFailRunClaimedByNewAttempt() {
        Companion companion = persistedCompanion();
        FeedingRun stale = processingRun(companion, 102L);
        FeedingRun claimed = stale.restarted(NOW.plusSeconds(1));
        when(runRepository.fail(stale.id(), stale.revision(), "ANALYSIS_FAILED", "分析失败", NOW))
                .thenReturn(false);
        when(runRepository.findByKey(companion.id(), KEY)).thenReturn(Optional.of(claimed));

        assertThatThrownBy(() -> coordinator.fail(stale, "ANALYSIS_FAILED", "分析失败"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("喂养运行状态已变化，请重试");

        verify(runRepository).fail(stale.id(), stale.revision(), "ANALYSIS_FAILED", "分析失败", NOW);
        verify(runRepository, never()).fail(claimed.id(), claimed.revision(),
                "ANALYSIS_FAILED", "分析失败", NOW);
    }

    @Test
    void completionAppliesMoodRelationshipAndMemoryCandidateAtomically() {
        Companion companion = persistedCompanion();
        FeedingRun run = processingRun(companion, 102L);
        CompanionRelationship relationship = CompanionRelationship.initial(companion.id(), 7L);
        PictureNutrition nutrition = new PictureNutrition(42L, TraitDelta.zero(), Map.of(),
                "演示营养", NutritionProvenance.demo(),
                new MoodImpact(bd("4.00"), bd("2.00"), bd("0.00"), bd("1.00"), bd("0.00")),
                "伙伴记得一张演示图片。");
        when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));
        when(companionRepository.save(any(), eq(companion.revision()))).thenReturn(true);
        when(growthRepository.append(any())).thenAnswer(invocation ->
                invocation.<GrowthRecord>getArgument(0).withId(31L));
        when(runRepository.complete(run.id(), run.revision(), 31L, NOW)).thenReturn(true);
        when(moodRepository.findByCompanionId(companion.id())).thenReturn(Optional.empty());
        when(relationshipRepository.findByCompanionAndSubject(companion.id(), 7L))
                .thenReturn(Optional.of(relationship));

        coordinator.complete(run, nutrition);

        ArgumentCaptor<CompanionMood> moodInsert = ArgumentCaptor.forClass(CompanionMood.class);
        verify(moodRepository).insert(moodInsert.capture());
        assertThat(moodInsert.getValue().energy()).isEqualByComparingTo("4.00");
        assertThat(moodInsert.getValue().joy()).isEqualByComparingTo("2.00");
        verify(relationshipRepository).save(any(), eq(relationship.revision()));
        ArgumentCaptor<CompanionMemory> memoryAppend = ArgumentCaptor.forClass(CompanionMemory.class);
        verify(memoryRepository).append(memoryAppend.capture());
        assertThat(memoryAppend.getValue().status()).isEqualTo(MemoryStatus.PENDING);
        assertThat(memoryAppend.getValue().content()).isEqualTo("伙伴记得一张演示图片。");
        assertThat(memoryAppend.getValue().confidence()).isEqualByComparingTo("0.500");
    }

    @Test
    void revisitCompletionSkipsMemoryCandidateButStillUpdatesRelationship() {
        Companion companion = persistedCompanion();
        FeedingRun run = processingRun(companion, 102L);
        CompanionRelationship relationship = CompanionRelationship.initial(companion.id(), 7L);
        when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));
        when(growthRepository.hasFullFeed(companion.id(), 102L)).thenReturn(true);
        when(growthRepository.sumLifeExperienceSince(eq(companion.id()), any())).thenReturn(20L);
        when(growthRepository.sumRevisitExperience(companion.id(), 102L)).thenReturn(1L);
        when(companionRepository.save(any(), eq(companion.revision()))).thenReturn(true);
        when(growthRepository.append(any())).thenAnswer(invocation ->
                invocation.<GrowthRecord>getArgument(0).withId(31L));
        when(runRepository.complete(run.id(), run.revision(), 31L, NOW)).thenReturn(true);
        when(relationshipRepository.findByCompanionAndSubject(companion.id(), 7L))
                .thenReturn(Optional.of(relationship));

        coordinator.complete(run, PictureNutrition.demo(42L, TraitDelta.zero(), Map.of(), "演示营养"));

        verify(memoryRepository, never()).append(any());
        ArgumentCaptor<CompanionRelationship> relationshipSave = ArgumentCaptor.forClass(CompanionRelationship.class);
        verify(relationshipRepository).save(relationshipSave.capture(), eq(relationship.revision()));
        assertThat(relationshipSave.getValue().familiarity()).isEqualByComparingTo("2.00");
        assertThat(relationshipSave.getValue().trust()).isEqualByComparingTo("0.00");
    }

    @Test
    void moodApplyConflictsRollBackWholeCompletion() {
        Companion companion = persistedCompanion();
        FeedingRun run = processingRun(companion, 102L);
        CompanionRelationship relationship = CompanionRelationship.initial(companion.id(), 7L);
        CompanionMood existing = new CompanionMood(51L, companion.id(),
                bd("30.00"), bd("10.00"), bd("0.00"), bd("0.00"), bd("0.00"),
                2L, NOW.minusSeconds(3600));
        when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));
        when(companionRepository.save(any(), eq(companion.revision()))).thenReturn(true);
        when(growthRepository.append(any())).thenAnswer(invocation ->
                invocation.<GrowthRecord>getArgument(0).withId(31L));
        when(moodRepository.findByCompanionId(companion.id())).thenReturn(Optional.of(existing));
        when(moodRepository.save(any(), anyLong())).thenReturn(false);
        when(relationshipRepository.findByCompanionAndSubject(companion.id(), 7L))
                .thenReturn(Optional.of(relationship));

        assertThatThrownBy(() -> coordinator.complete(run,
                new PictureNutrition(42L, TraitDelta.zero(), Map.of(), "演示营养",
                        NutritionProvenance.demo(),
                        new MoodImpact(bd("4.00"), bd("0.00"), bd("0.00"), bd("0.00"), bd("0.00")), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("伙伴情绪状态已变化，请重试");
        verify(runRepository, never()).complete(anyLong(), anyLong(), anyLong(), any());
        verify(memoryRepository, never()).append(any());
    }

    private Companion persistedCompanion() {
        return Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
    }

    private FeedingRun processingRun(Companion companion, long pictureId) {
        return FeedingRun.processing(companion.id(), 7L, pictureId, KEY, fingerprint(pictureId), CORRELATION,
                NutritionMode.DEMO_DETERMINISTIC, false, NOW).persistedAs(21L);
    }

    private static String fingerprint(long pictureId) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(("pictureId=" + pictureId).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
