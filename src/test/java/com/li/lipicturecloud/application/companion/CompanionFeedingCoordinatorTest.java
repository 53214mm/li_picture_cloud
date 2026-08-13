package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.config.CompanionFeatureProperties;
import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
import com.li.lipicturecloud.domain.companion.CompanionSkill;
import com.li.lipicturecloud.domain.companion.FeedingRun;
import com.li.lipicturecloud.domain.companion.FeedingRunRepository;
import com.li.lipicturecloud.domain.companion.GrowthRecord;
import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import com.li.lipicturecloud.domain.companion.NutritionMode;
import com.li.lipicturecloud.domain.companion.PictureNutrition;
import com.li.lipicturecloud.domain.companion.TraitDelta;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
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
    private CompanionFeedingCoordinator coordinator;

    @BeforeEach
    void setUp() {
        companionRepository = mock(CompanionRepository.class);
        growthRepository = mock(GrowthRecordRepository.class);
        runRepository = mock(FeedingRunRepository.class);
        PictureNutritionAnalyzer analyzer = mock(PictureNutritionAnalyzer.class);
        CompanionBalance balance = CompanionBalance.v1();
        CompanionViewAssembler assembler = new CompanionViewAssembler(balance, analyzer);
        coordinator = new CompanionFeedingCoordinator(companionRepository, growthRepository, runRepository,
                balance, assembler, Clock.fixed(NOW, ZoneOffset.UTC), new CompanionFeatureProperties());
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
    void completionReadsClockOnlyOnceForDailyBoundaryAndAuditTime() {
        Companion companion = persistedCompanion();
        FeedingRun run = processingRun(companion, 102L);
        Clock singleUseClock = mock(Clock.class);
        when(singleUseClock.instant()).thenReturn(NOW);
        PictureNutritionAnalyzer analyzer = mock(PictureNutritionAnalyzer.class);
        CompanionViewAssembler localAssembler = new CompanionViewAssembler(CompanionBalance.v1(), analyzer);
        CompanionFeedingCoordinator local = new CompanionFeedingCoordinator(companionRepository,
                growthRepository, runRepository, CompanionBalance.v1(), localAssembler,
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
    void staleAttemptCannotRejectRunClaimedByNewAttempt() {
        Companion companion = persistedCompanion();
        FeedingRun stale = processingRun(companion, 102L);
        FeedingRun claimed = stale.restarted(NOW.plusSeconds(1));
        when(runRepository.reject(stale.id(), stale.revision(), "PICTURE_UNAVAILABLE", "图片不可用", NOW))
                .thenReturn(false);
        when(runRepository.findByKey(companion.id(), KEY)).thenReturn(Optional.of(claimed));

        assertThatThrownBy(() -> coordinator.reject(stale, "PICTURE_UNAVAILABLE", "图片不可用"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("喂养运行状态已变化，请重试");

        verify(runRepository, never()).reject(claimed.id(), claimed.revision(),
                "PICTURE_UNAVAILABLE", "图片不可用", NOW);
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
}
