package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.companion.view.CompanionContractView;
import com.li.lipicturecloud.application.companion.view.CompanionProposalView;
import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionAutonomyContract;
import com.li.lipicturecloud.domain.companion.CompanionAutonomyContractRepository;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.domain.companion.CompanionProposal;
import com.li.lipicturecloud.domain.companion.CompanionProposalReactionRepository;
import com.li.lipicturecloud.domain.companion.CompanionProposalRepository;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import com.li.lipicturecloud.domain.companion.ProposalOpportunityType;
import com.li.lipicturecloud.domain.picture.PictureAsset;
import com.li.lipicturecloud.domain.picture.PictureAssetRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant.PICTURE_VIEW;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanionProposalServiceTest {

    // 上海 10:00（白天，避开默认安静时段）
    private static final Instant NOW = Instant.parse("2026-08-14T02:00:00Z");
    private final AuthorizationSubject subject = AuthorizationSubject.user(7L);

    private CompanionRepository companionRepository;
    private CompanionAutonomyContractRepository contractRepository;
    private CompanionProposalRepository proposalRepository;
    private CompanionProposalReactionRepository reactionRepository;
    private ProposalOpportunityEvaluator evaluator;
    private WeeklyReviewOpportunitySource opportunitySource;
    private CompanionProposalService service;

    @BeforeEach
    void setUp() {
        companionRepository = mock(CompanionRepository.class);
        contractRepository = mock(CompanionAutonomyContractRepository.class);
        proposalRepository = mock(CompanionProposalRepository.class);
        reactionRepository = mock(CompanionProposalReactionRepository.class);
        opportunitySource = mock(WeeklyReviewOpportunitySource.class);
        evaluator = mock(ProposalOpportunityEvaluator.class);
        when(evaluator.reachesProposalThreshold(any())).thenReturn(true);
        when(opportunitySource.type()).thenReturn(ProposalOpportunityType.WEEKLY_REVIEW);
        // 默认没有可观察机会；用例需要时再 stub observe/materialize。
        when(opportunitySource.observe(anyLong(), anyLong(), any())).thenReturn(Optional.empty());
        when(proposalRepository.append(any())).thenAnswer(invocation ->
                invocation.<CompanionProposal>getArgument(0).withId(61L));
        when(proposalRepository.findActive(anyLong(), anyInt())).thenReturn(List.of());
        when(proposalRepository.findRecent(anyLong(), anyInt())).thenReturn(List.of());
        when(contractRepository.createIfAbsent(anyLong(), anyLong()))
                .thenAnswer(invocation -> CompanionAutonomyContract.initial(
                        invocation.getArgument(0), invocation.getArgument(1)));
        when(contractRepository.save(any(), anyLong())).thenReturn(true);
        when(proposalRepository.save(any(), anyLong())).thenReturn(true);
        service = new CompanionProposalService(companionRepository, contractRepository,
                proposalRepository, reactionRepository, List.of(opportunitySource), List.of(), evaluator, CompanionBalance.v1(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void contractDefaultsToOffAndCanBeUpdated() {
        Companion companion = persistedCompanion();
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));

        CompanionContractView initial = service.contract(subject);
        assertThat(initial.active()).isFalse();
        assertThat(initial.maxFrequencyHours()).isEqualTo(72);

        CompanionContractView updated = service.updateContract(subject, true,
                LocalTime.of(22, 0), LocalTime.of(7, 0), 24);
        assertThat(updated.active()).isTrue();
        assertThat(updated.maxFrequencyHours()).isEqualTo(24);
    }

    @Test
    void activeProposalIsGeneratedWhenContractAllowsAndOpportunityExists() {
        Companion companion = persistedCompanion();
        when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));
        when(contractRepository.createIfAbsent(companion.id(), 7L))
                .thenAnswer(invocation -> CompanionAutonomyContract.initial(
                        invocation.getArgument(0), invocation.getArgument(1)).updated(
                        true, LocalTime.of(23, 0), LocalTime.of(8, 0), 72));
        when(opportunitySource.observe(companion.id(), 7L, NOW))
                .thenReturn(Optional.of(new OpportunityObservation(ProposalOpportunityType.WEEKLY_REVIEW)));
        when(opportunitySource.materialize(any(), anyLong(), anyLong(), any()))
                .thenReturn(Optional.of(new ProposalOpportunity(ProposalOpportunityType.WEEKLY_REVIEW,
                        new BigDecimal("30.00"), "这周你喂了我 3 次。想听我讲一段我们的故事吗？")));

        CompanionProposalView view = service.active(subject);

        assertThat(view).isNotNull();
        assertThat(view.status()).isEqualTo("PENDING");
        assertThat(view.opportunityType()).isEqualTo("WEEKLY_REVIEW");
        verify(proposalRepository).append(any());
    }

    @Test
    void contractDisabledGateIsRecordedWithTheRealOpportunityType() {
        Companion companion = persistedCompanion();
        when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));
        // 契约默认关闭，但真实机会可观察：Observe 先于守门，拦截日志必须带上机会类型。
        when(opportunitySource.observe(companion.id(), 7L, NOW))
                .thenReturn(Optional.of(new OpportunityObservation(ProposalOpportunityType.WEEKLY_REVIEW)));
        ListAppender<ILoggingEvent> logs = captureProposalServiceLogs();
        try {
            CompanionProposalView view = service.active(subject);

            assertThat(view).isNull();
            // 守门失败不落库、不物化候选：只记录"被拦的是哪类机会"。
            verify(opportunitySource).observe(companion.id(), 7L, NOW);
            verify(opportunitySource, never()).materialize(any(), anyLong(), anyLong(), any());
            verify(proposalRepository, never()).append(any());
            assertThat(logs.list).anyMatch(event -> event.getFormattedMessage()
                    .contains("companion_proposal_gated subjectId=7 proposalId=none type=WEEKLY_REVIEW "
                            + "reason=CONTRACT_DISABLED"));
        } finally {
            releaseProposalLogs(logs);
        }
    }

    @Test
    void contractDisabledNeverScoresOrMaterializesTheCandidate() {
        Companion companion = persistedCompanion();
        GrowthRecordRepository growth = mock(GrowthRecordRepository.class);
        when(growth.countSince(11L, NOW.minus(java.time.Duration.ofDays(7)))).thenReturn(3L);
        WeeklyReviewOpportunitySource realSource = new WeeklyReviewOpportunitySource(growth, evaluator);
        CompanionProposalService localService = new CompanionProposalService(companionRepository,
                contractRepository, proposalRepository, reactionRepository, List.of(realSource), List.of(), evaluator, CompanionBalance.v1(), Clock.fixed(NOW, ZoneOffset.UTC));
        when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));
        ListAppender<ILoggingEvent> logs = captureProposalServiceLogs();
        try {
            CompanionProposalView view = localService.active(subject);

            assertThat(view).isNull();
            verify(proposalRepository, never()).append(any());
            // 硬门禁边界：守门失败后，冲动评分（情绪读取/写回的唯一入口）不被调用，
            // 提案不落库——契约关闭时只做轻量观察与 gated 记录。
            verify(evaluator, never()).score(anyLong(), anyLong(), any(), any());
            verify(evaluator, never()).reachesProposalThreshold(any());
            assertThat(logs.list).anyMatch(event -> event.getFormattedMessage()
                    .contains("companion_proposal_gated subjectId=7 proposalId=none type=WEEKLY_REVIEW "
                            + "reason=CONTRACT_DISABLED"));
        } finally {
            releaseProposalLogs(logs);
        }
    }

    @Test
    void revokedSimilarPictureIsNotGatedUnderADisabledContract() {
        // 历史喂养图片仍存在但已被撤权：相似图片并不存在"真实机会"——
        // 契约关闭时只记 NO_CANDIDATE，绝不记录 SIMILAR_STORY 被守门拦截。
        Companion companion = persistedCompanion();
        when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));
        GrowthRecordRepository growth = mock(GrowthRecordRepository.class);
        PictureAssetRepository pictures = mock(PictureAssetRepository.class);
        SpaceAuthorizationAccessService authorization = mock(SpaceAuthorizationAccessService.class);
        when(growth.findRecentFedPictureIds(11L, 5)).thenReturn(List.of(101L));
        when(pictures.findAssetById(101L))
                .thenReturn(Optional.of(new PictureAsset(101L, 7L, 30L)));
        doThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR, "缺少权限"))
                .when(authorization).checkForUser(PICTURE_VIEW, 101L, 7L);
        SimilarStoryOpportunitySource similar = new SimilarStoryOpportunitySource(
                growth, pictures, authorization, evaluator);
        CompanionProposalService localService = new CompanionProposalService(companionRepository,
                contractRepository, proposalRepository, reactionRepository, List.of(similar), List.of(), evaluator, CompanionBalance.v1(), Clock.fixed(NOW, ZoneOffset.UTC));
        ListAppender<ILoggingEvent> logs = captureProposalServiceLogs();
        try {
            CompanionProposalView view = localService.active(subject);

            assertThat(view).isNull();
            assertThat(logs.list).noneMatch(event ->
                    event.getFormattedMessage().contains("companion_proposal_gated"));
            assertThat(logs.list).anyMatch(event -> event.getFormattedMessage()
                    .contains("companion_proposal_opportunity subjectId=7 type=SIMILAR_STORY "
                            + "result=NO_CANDIDATE"));
        } finally {
            releaseProposalLogs(logs);
        }
    }

    @Test
    void similarPictureWithoutNewSpacePicturesIsNotGatedUnderADisabledContract() {
        Companion companion = persistedCompanion();
        when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));
        GrowthRecordRepository growth = mock(GrowthRecordRepository.class);
        PictureAssetRepository pictures = mock(PictureAssetRepository.class);
        SpaceAuthorizationAccessService authorization = mock(SpaceAuthorizationAccessService.class);
        when(growth.findRecentFedPictureIds(11L, 5)).thenReturn(List.of(101L));
        when(pictures.findAssetById(101L))
                .thenReturn(Optional.of(new PictureAsset(101L, 7L, 30L)));
        when(pictures.countRecentInSpace(30L, NOW.minus(java.time.Duration.ofDays(7))))
                .thenReturn(1L);
        SimilarStoryOpportunitySource similar = new SimilarStoryOpportunitySource(
                growth, pictures, authorization, evaluator);
        CompanionProposalService localService = new CompanionProposalService(companionRepository,
                contractRepository, proposalRepository, reactionRepository, List.of(similar), List.of(), evaluator, CompanionBalance.v1(), Clock.fixed(NOW, ZoneOffset.UTC));
        ListAppender<ILoggingEvent> logs = captureProposalServiceLogs();
        try {
            CompanionProposalView view = localService.active(subject);

            assertThat(view).isNull();
            assertThat(logs.list).noneMatch(event ->
                    event.getFormattedMessage().contains("companion_proposal_gated"));
        } finally {
            releaseProposalLogs(logs);
        }
    }

    @Test
    void absenceOfAnyCandidateIsNeverCountedAsGated() {
        Companion companion = persistedCompanion();
        when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));
        ListAppender<ILoggingEvent> logs = captureProposalServiceLogs();
        try {
            CompanionProposalView view = service.active(subject);

            assertThat(view).isNull();
            // 没有真实机会时只记 NO_CANDIDATE，绝不能计为 gated（避免把轮询当拦截率分子）。
            assertThat(logs.list).noneMatch(event ->
                    event.getFormattedMessage().contains("companion_proposal_gated"));
            assertThat(logs.list).anyMatch(event -> event.getFormattedMessage()
                    .contains("companion_proposal_opportunity subjectId=7 type=WEEKLY_REVIEW "
                            + "result=NO_CANDIDATE"));
        } finally {
            releaseProposalLogs(logs);
        }
    }

    @Test
    void existingPendingProposalIsReturnedWithoutGeneratingAnother() {
        Companion companion = persistedCompanion();
        CompanionProposal pending = CompanionProposal.pending(companion.id(), 7L,
                ProposalOpportunityType.WEEKLY_REVIEW, new BigDecimal("10.00"),
                "这周你喂了我 3 次。想听我讲一段我们的故事吗？", NOW).withId(61L);
        when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));
        when(proposalRepository.findActive(companion.id(), 5)).thenReturn(List.of(pending));

        CompanionProposalView view = service.active(subject);

        assertThat(view.id()).isEqualTo(61L);
        verify(opportunitySource, never()).observe(anyLong(), anyLong(), any());
        verify(proposalRepository, never()).append(any());
    }

    @Test
    void opportunitySourcesShortCircuitInOrder() {
        Companion companion = persistedCompanion();
        CompanionOpportunitySource emptySource = mock(CompanionOpportunitySource.class);
        CompanionOpportunitySource hitSource = mock(CompanionOpportunitySource.class);
        CompanionOpportunitySource thirdSource = mock(CompanionOpportunitySource.class);
        CompanionProposalService localService = new CompanionProposalService(companionRepository,
                contractRepository, proposalRepository, reactionRepository,
                List.of(emptySource, hitSource, thirdSource), List.of(), evaluator, CompanionBalance.v1(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));
        when(contractRepository.createIfAbsent(companion.id(), 7L))
                .thenAnswer(invocation -> CompanionAutonomyContract.initial(
                        invocation.getArgument(0), invocation.getArgument(1)).updated(
                        true, LocalTime.of(23, 0), LocalTime.of(8, 0), 72));
        when(emptySource.observe(companion.id(), 7L, NOW)).thenReturn(Optional.empty());
        when(hitSource.observe(companion.id(), 7L, NOW))
                .thenReturn(Optional.of(new OpportunityObservation(ProposalOpportunityType.ANNIVERSARY)));
        when(hitSource.materialize(any(), anyLong(), anyLong(), any()))
                .thenReturn(Optional.of(new ProposalOpportunity(ProposalOpportunityType.ANNIVERSARY,
                        new BigDecimal("20.00"), "往年的今天我们相遇过。")));

        CompanionProposalView view = localService.active(subject);

        assertThat(view).isNotNull();
        assertThat(view.opportunityType()).isEqualTo("ANNIVERSARY");
        verify(emptySource).observe(companion.id(), 7L, NOW);
        verify(hitSource).observe(companion.id(), 7L, NOW);
        // 命中后短路：第三个机会源不再调用。
        verify(thirdSource, never()).observe(anyLong(), anyLong(), any());
        verify(proposalRepository).append(any());
    }

    @Test
    void zeroImpulseCandidateIsInterceptedAndTheNextOpportunitySourceStillRuns() {
        Companion companion = persistedCompanion();
        CompanionOpportunitySource zeroSource = mock(CompanionOpportunitySource.class);
        CompanionOpportunitySource nextSource = mock(CompanionOpportunitySource.class);
        CompanionProposalService localService = new CompanionProposalService(companionRepository,
                contractRepository, proposalRepository, reactionRepository,
                List.of(zeroSource, nextSource), List.of(), evaluator, CompanionBalance.v1(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));
        when(contractRepository.createIfAbsent(companion.id(), 7L))
                .thenAnswer(invocation -> CompanionAutonomyContract.initial(
                        invocation.getArgument(0), invocation.getArgument(1)).updated(
                        true, LocalTime.of(23, 0), LocalTime.of(8, 0), 72));
        // 第一个机会源的候选冲动为 0（没有任何正向情绪/关系积累）→ 被阈值拦截。
        when(zeroSource.observe(companion.id(), 7L, NOW))
                .thenReturn(Optional.of(new OpportunityObservation(ProposalOpportunityType.WEEKLY_REVIEW)));
        when(zeroSource.materialize(any(), anyLong(), anyLong(), any()))
                .thenReturn(Optional.of(new ProposalOpportunity(ProposalOpportunityType.WEEKLY_REVIEW,
                        new BigDecimal("0.00"), "这周你喂了我 1 次。")));
        when(nextSource.observe(companion.id(), 7L, NOW))
                .thenReturn(Optional.of(new OpportunityObservation(ProposalOpportunityType.ANNIVERSARY)));
        when(nextSource.materialize(any(), anyLong(), anyLong(), any()))
                .thenReturn(Optional.of(new ProposalOpportunity(ProposalOpportunityType.ANNIVERSARY,
                        new BigDecimal("25.00"), "往年的今天我们相遇过。")));
        when(evaluator.reachesProposalThreshold(argThat(score -> score.signum() == 0)))
                .thenReturn(false);

        CompanionProposalView view = localService.active(subject);

        assertThat(view).isNotNull();
        assertThat(view.opportunityType()).isEqualTo("ANNIVERSARY");
        // 只有通过阈值的那条候选落库。
        ArgumentCaptor<CompanionProposal> captor = ArgumentCaptor.forClass(CompanionProposal.class);
        verify(proposalRepository).append(captor.capture());
        assertThat(captor.getValue().opportunityType()).isEqualTo(ProposalOpportunityType.ANNIVERSARY);
        verify(zeroSource).observe(companion.id(), 7L, NOW);
        verify(zeroSource).materialize(any(), anyLong(), anyLong(), any());
        verify(nextSource).observe(companion.id(), 7L, NOW);
        verify(nextSource).materialize(any(), anyLong(), anyLong(), any());
    }

    @Test
    void zeroImpulseCandidateAloneProducesNoProposal() {
        Companion companion = persistedCompanion();
        when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));
        when(contractRepository.createIfAbsent(companion.id(), 7L))
                .thenAnswer(invocation -> CompanionAutonomyContract.initial(
                        invocation.getArgument(0), invocation.getArgument(1)).updated(
                        true, LocalTime.of(23, 0), LocalTime.of(8, 0), 72));
        when(opportunitySource.observe(companion.id(), 7L, NOW))
                .thenReturn(Optional.of(new OpportunityObservation(ProposalOpportunityType.WEEKLY_REVIEW)));
        when(opportunitySource.materialize(any(), anyLong(), anyLong(), any()))
                .thenReturn(Optional.of(new ProposalOpportunity(ProposalOpportunityType.WEEKLY_REVIEW,
                        new BigDecimal("0.00"), "这周你喂了我 1 次。想听我讲一段我们的故事吗？")));
        when(evaluator.reachesProposalThreshold(argThat(score -> score.signum() == 0)))
                .thenReturn(false);

        CompanionProposalView view = service.active(subject);

        assertThat(view).isNull();
        verify(proposalRepository, never()).append(any());
    }

    @Test
    void scoldSuppressesProposalAndRepeatedScoldsAdjustCuriosity() {
        Companion companion = persistedCompanion();
        CompanionProposal pending = CompanionProposal.pending(companion.id(), 7L,
                ProposalOpportunityType.WEEKLY_REVIEW, new BigDecimal("10.00"),
                "这周你喂了我 3 次。想听我讲一段我们的故事吗？", NOW).withId(61L);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(proposalRepository.findById(61L)).thenReturn(Optional.of(pending));
        when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));
        when(companionRepository.save(any(), anyLong())).thenReturn(true);
        when(reactionRepository.countScoldsSince(7L, NOW.minus(java.time.Duration.ofDays(30))))
                .thenReturn(3L);

        CompanionProposalView view = service.scold(subject, 61L);

        assertThat(view.status()).isEqualTo("SUPPRESSED");
        assertThat(view.gateResult()).isEqualTo("SCOLDED");
        // 第 3 次敲打触发一次"好奇"性格下调。
        verify(companionRepository).save(any(), anyLong());
    }

    @Test
    void reactingOnForeignProposalIsInvisible() {
        Companion companion = persistedCompanion();
        CompanionProposal other = CompanionProposal.pending(999L, 8L,
                ProposalOpportunityType.WEEKLY_REVIEW, new BigDecimal("10.00"), "别人的提案", NOW).withId(61L);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(proposalRepository.findById(61L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.accept(subject, 61L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("提案不存在");
    }

    /** 契约关闭（守门未通过）时不得把机会投递给机会监听者（阶段 5 配方 WHEN）。 */
    @Test
    void opportunityListenersAreNotNotifiedWhenTheGateBlocks() {
        Companion companion = persistedCompanion();
        CompanionOpportunityListener listener = mock(CompanionOpportunityListener.class);
        CompanionProposalService localService = new CompanionProposalService(companionRepository,
                contractRepository, proposalRepository, reactionRepository,
                List.of(opportunitySource), List.of(listener), evaluator, CompanionBalance.v1(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));
        when(opportunitySource.observe(companion.id(), 7L, NOW))
                .thenReturn(Optional.of(new OpportunityObservation(ProposalOpportunityType.WEEKLY_REVIEW)));

        // 契约默认关闭 → CONTRACT_DISABLED，机会不得外泄给监听者。
        assertThat(localService.active(subject)).isNull();
        verify(listener, never()).onOpportunity(anyLong(), anyLong(), any(), any(), any());
    }

    /** 守门通过后机会被投递给监听者：带真实机会类型与图片事实，且不产生任何自动执行。 */
    @Test
    void opportunityListenersReceiveTheObservedOpportunityAfterTheGatePasses() {
        Companion companion = persistedCompanion();
        CompanionOpportunityListener listener = mock(CompanionOpportunityListener.class);
        CompanionProposalService localService = new CompanionProposalService(companionRepository,
                contractRepository, proposalRepository, reactionRepository,
                List.of(opportunitySource), List.of(listener), evaluator, CompanionBalance.v1(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));
        when(contractRepository.createIfAbsent(companion.id(), 7L))
                .thenAnswer(invocation -> CompanionAutonomyContract.initial(
                        invocation.getArgument(0), invocation.getArgument(1)).updated(
                        true, LocalTime.of(23, 0), LocalTime.of(8, 0), 72));
        when(opportunitySource.observe(companion.id(), 7L, NOW)).thenReturn(Optional.of(
                new OpportunityObservation(ProposalOpportunityType.SIMILAR_STORY, 102L, 10L, 3L)));
        when(opportunitySource.materialize(any(), anyLong(), anyLong(), any()))
                .thenReturn(Optional.of(new ProposalOpportunity(ProposalOpportunityType.SIMILAR_STORY,
                        new BigDecimal("30.00"), "这两张照片很像。")));

        assertThat(localService.active(subject)).isNotNull();

        verify(listener).onOpportunity(7L, companion.id(), "SIMILAR_STORY", 102L, NOW);
    }

    /** 监听者失败不得影响伙伴提案主链路（配方 WHEN 出问题不能拖垮对话体验）。 */
    @Test
    void listenerFailuresDoNotBreakTheProposalFlow() {
        Companion companion = persistedCompanion();
        CompanionOpportunityListener listener = mock(CompanionOpportunityListener.class);
        org.mockito.Mockito.doThrow(new RuntimeException("recipe trigger down"))
                .when(listener).onOpportunity(anyLong(), anyLong(), any(), any(), any());
        CompanionProposalService localService = new CompanionProposalService(companionRepository,
                contractRepository, proposalRepository, reactionRepository,
                List.of(opportunitySource), List.of(listener), evaluator, CompanionBalance.v1(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));
        when(contractRepository.createIfAbsent(companion.id(), 7L))
                .thenAnswer(invocation -> CompanionAutonomyContract.initial(
                        invocation.getArgument(0), invocation.getArgument(1)).updated(
                        true, LocalTime.of(23, 0), LocalTime.of(8, 0), 72));
        when(opportunitySource.observe(companion.id(), 7L, NOW))
                .thenReturn(Optional.of(new OpportunityObservation(ProposalOpportunityType.WEEKLY_REVIEW)));
        when(opportunitySource.materialize(any(), anyLong(), anyLong(), any()))
                .thenReturn(Optional.of(new ProposalOpportunity(ProposalOpportunityType.WEEKLY_REVIEW,
                        new BigDecimal("30.00"), "这周你喂了我 3 次。")));

        assertThat(localService.active(subject)).isNotNull();
        verify(proposalRepository).append(any());
    }

    private Companion persistedCompanion() {
        return Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
    }

    /** 捕获 CompanionProposalService 的 INFO 日志（含 gated/opportunity 事件），便于断言观测字段。 */
    private static ListAppender<ILoggingEvent> captureProposalServiceLogs() {
        LoggerContext context = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(CompanionProposalService.class);
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(context);
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void releaseProposalLogs(ListAppender<ILoggingEvent> appender) {
        LoggerContext context = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        context.getLogger(CompanionProposalService.class).detachAppender(appender);
    }
}
