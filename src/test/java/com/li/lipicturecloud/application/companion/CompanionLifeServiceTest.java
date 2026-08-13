package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.companion.view.FeedPictureResult;
import com.li.lipicturecloud.config.CompanionFeatureProperties;
import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
import com.li.lipicturecloud.domain.companion.FeedingRun;
import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import com.li.lipicturecloud.domain.companion.NutritionMode;
import com.li.lipicturecloud.domain.companion.NutritionPolicy;
import com.li.lipicturecloud.domain.companion.PictureNutrition;
import com.li.lipicturecloud.domain.companion.TraitDelta;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant.PICTURE_VIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanionLifeServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T08:00:00Z");
    private static final String KEY = "6f26d166-0a82-4d9f-8a61-6c21cf2e59d0";
    private final AuthorizationSubject subject = AuthorizationSubject.user(7L);

    private CompanionRepository companionRepository;
    private GrowthRecordRepository growthRepository;
    private CompanionFeedingCoordinator coordinator;
    private SpaceAuthorizationAccessService authorization;
    private PictureNutritionAnalyzer analyzer;
    private CompanionFeatureProperties properties;
    private CompanionViewAssembler assembler;
    private CompanionLifeService service;

    @BeforeEach
    void setUp() {
        companionRepository = mock(CompanionRepository.class);
        growthRepository = mock(GrowthRecordRepository.class);
        coordinator = mock(CompanionFeedingCoordinator.class);
        authorization = mock(SpaceAuthorizationAccessService.class);
        analyzer = mock(PictureNutritionAnalyzer.class);
        properties = new CompanionFeatureProperties();
        when(analyzer.policy()).thenReturn(NutritionPolicy.DEMO_ONLY);
        when(analyzer.mode()).thenReturn(NutritionMode.DEMO_DETERMINISTIC);
        when(analyzer.contentUnderstood()).thenReturn(false);
        assembler = new CompanionViewAssembler(CompanionBalance.v1(), analyzer);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:companion_life_service;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        service = new CompanionLifeService(companionRepository, growthRepository, coordinator,
                authorization, analyzer, assembler, properties, CompanionBalance.v1(),
                new DataSourceTransactionManager(dataSource));
    }

    @Test
    void normalizesMissingPicturesBeforeAnalyzerUse() {
        Companion companion = persistedCompanion();
        FeedingRun run = processingRun(companion);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(coordinator.reserve(any(), any(), anyLong(), anyString(), anyString(), anyString(),
                any(NutritionPolicy.class), nullable(String.class), nullable(String.class)))
                .thenReturn(FeedReservation.started(run));
        doThrow(new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在"))
                .when(authorization).checkForUser(PICTURE_VIEW, 102L, 7L);

        assertThatThrownBy(() -> service.feed(new FeedPictureCommand(subject, 102L, KEY)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("图片不可用或无权访问");

        verify(coordinator).reject(run, "PICTURE_UNAVAILABLE", "图片不可用或无权访问");
        verify(analyzer, never()).analyze(any());
    }

    @Test
    void completedReplayRechecksPermissionAndReturnsOriginalResult() {
        Companion companion = persistedCompanion();
        FeedingRun completed = processingRun(companion).completed(31L, NOW.plusSeconds(1));
        FeedPictureResult original = new FeedPictureResult("GROWN", completed.correlationId(),
                assembler.companion(companion), null);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(coordinator.reserve(any(), any(), anyLong(), anyString(), anyString(), anyString(),
                any(NutritionPolicy.class), nullable(String.class), nullable(String.class)))
                .thenReturn(FeedReservation.replay(completed, original));

        assertThat(service.feed(new FeedPictureCommand(subject, 102L, KEY))).isEqualTo(original);
        verify(authorization).checkForUser(PICTURE_VIEW, 102L, 7L);
        verify(analyzer, never()).analyze(any());
    }

    @Test
    void homeReadsAggregateAndHistoryInsideOneRepeatableReadSnapshot() {
        Companion companion = persistedCompanion();
        when(companionRepository.findByOwnerId(7L)).thenAnswer(invocation -> {
            assertReadSnapshot();
            return Optional.of(companion);
        });
        when(growthRepository.findRecent(companion.id(), 20)).thenAnswer(invocation -> {
            assertReadSnapshot();
            return List.of();
        });

        var home = service.home(subject);

        assertThat(home.companion().id()).isEqualTo(companion.id());
        assertThat(home.recentGrowth()).isEmpty();
    }

    @Test
    void analyzerFailurePersistsSafeFailureWithoutLeakingItsMessage() {
        Companion companion = persistedCompanion();
        FeedingRun run = processingRun(companion);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(coordinator.reserve(any(), any(), anyLong(), anyString(), anyString(), anyString(),
                any(NutritionPolicy.class), nullable(String.class), nullable(String.class)))
                .thenReturn(FeedReservation.started(run));
        when(analyzer.analyze(any())).thenThrow(new IllegalStateException("provider-token=secret"));

        assertThatThrownBy(() -> service.feed(new FeedPictureCommand(subject, 102L, KEY)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("本次没有消化成功，图片未被消耗");
        verify(coordinator).fail(run, "NUTRITION_FAILED", "本次没有消化成功，图片未被消耗");
        verify(coordinator, never()).complete(any(), any());
    }

    @Test
    void safeVisionFailureKeepsItsCategoryForAuditWithoutLeakingProviderDetails() {
        Companion companion = persistedCompanion();
        FeedingRun run = processingRun(companion);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(coordinator.reserve(any(), any(), anyLong(), anyString(), anyString(), anyString(),
                any(NutritionPolicy.class), nullable(String.class), nullable(String.class)))
                .thenReturn(FeedReservation.started(run));
        when(analyzer.analyze(any())).thenThrow(new VisionProviderException("VISION_CREDENTIALS", "provider-token=secret"));

        assertThatThrownBy(() -> service.feed(new FeedPictureCommand(subject, 102L, KEY)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("本次没有消化成功，图片未被消耗");

        verify(coordinator).fail(run, "VISION_CREDENTIALS", "本次没有消化成功，图片未被消耗");
    }

    @Test
    void familiarPictureUsesAnalyzerShortcutInsteadOfFullAnalysis() {
        Companion companion = persistedCompanion();
        FeedingRun run = processingRun(companion);
        PictureNutrition familiar = PictureNutrition.fromObservation(25L, TraitDelta.zero(), Map.of(), "元数据营养");
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(coordinator.reserve(any(), any(), anyLong(), anyString(), anyString(), anyString(),
                any(NutritionPolicy.class), nullable(String.class), nullable(String.class)))
                .thenReturn(FeedReservation.started(run));
        when(growthRepository.hasFullFeed(companion.id(), 102L)).thenReturn(true);
        when(analyzer.analyzeFamiliar(any())).thenReturn(familiar);

        service.feed(new FeedPictureCommand(subject, 102L, KEY));

        verify(analyzer).analyzeFamiliar(any());
        verify(analyzer, never()).analyze(any());
        verify(coordinator).complete(run, familiar);
    }

    @Test
    void disabledFeedingRejectsBeforeReservation() {
        properties.setFeedingEnabled(false);

        assertThatThrownBy(() -> service.feed(new FeedPictureCommand(subject, 102L, KEY)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("伙伴喂养已暂停");
        verify(coordinator, never()).reserve(any(), any(), anyLong(), anyString(), anyString(), anyString(),
                any(NutritionPolicy.class), nullable(String.class), nullable(String.class));
    }

    @Test
    void uppercaseIdempotencyKeyIsRejectedBeforeStateLookup() {
        assertThatThrownBy(() -> service.feed(new FeedPictureCommand(subject, 102L, "UPPERCASE-KEY-0001")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("喂养请求标识不合法");
        verify(companionRepository, never()).findByOwnerId(any(Long.class));
    }

    @Test
    void authorizationInfrastructureFailureDoesNotStrandStartedRun() {
        Companion companion = persistedCompanion();
        FeedingRun run = processingRun(companion);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(coordinator.reserve(any(), any(), anyLong(), anyString(), anyString(), anyString(),
                any(NutritionPolicy.class), nullable(String.class), nullable(String.class)))
                .thenReturn(FeedReservation.started(run));
        doThrow(new IllegalStateException("authorization database unavailable"))
                .when(authorization).checkForUser(PICTURE_VIEW, 102L, 7L);

        assertThatThrownBy(() -> service.feed(new FeedPictureCommand(subject, 102L, KEY)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("暂时无法校验图片访问权限，请重试");
        verify(coordinator).fail(run, "AUTHORIZATION_CHECK_FAILED", "暂时无法校验图片访问权限，请重试");
    }

    private void assertReadSnapshot() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
        assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                .isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
    }

    private Companion persistedCompanion() {
        return Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
    }

    private FeedingRun processingRun(Companion companion) {
        return FeedingRun.processing(companion.id(), 7L, 102L, KEY,
                "b15762071cb242d46414e9e7bfbc042a6da283b464d8c589a159a8a6109f4598",
                "fef53056-2d9f-467d-9b1d-1afe9a6638fe", NutritionMode.DEMO_DETERMINISTIC,
                false, NOW).persistedAs(21L);
    }
}
