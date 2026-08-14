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
import com.li.lipicturecloud.domain.companion.ProposalOpportunityType;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
    private WeeklyReviewOpportunitySource opportunitySource;
    private CompanionProposalService service;

    @BeforeEach
    void setUp() {
        companionRepository = mock(CompanionRepository.class);
        contractRepository = mock(CompanionAutonomyContractRepository.class);
        proposalRepository = mock(CompanionProposalRepository.class);
        reactionRepository = mock(CompanionProposalReactionRepository.class);
        opportunitySource = mock(WeeklyReviewOpportunitySource.class);
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
                proposalRepository, reactionRepository, List.of(opportunitySource),
                CompanionBalance.v1(), Clock.fixed(NOW, ZoneOffset.UTC));
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
        when(opportunitySource.findOpportunity(companion.id(), 7L, NOW))
                .thenReturn(Optional.of(new ProposalOpportunity(ProposalOpportunityType.WEEKLY_REVIEW,
                        new BigDecimal("30.00"), "这周你喂了我 3 次。想听我讲一段我们的故事吗？")));

        CompanionProposalView view = service.active(subject);

        assertThat(view).isNotNull();
        assertThat(view.status()).isEqualTo("PENDING");
        assertThat(view.opportunityType()).isEqualTo("WEEKLY_REVIEW");
        verify(proposalRepository).append(any());
    }

    @Test
    void activeProposalIsBlockedWhenContractIsDisabled() {
        Companion companion = persistedCompanion();
        when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));

        CompanionProposalView view = service.active(subject);

        assertThat(view).isNull();
        verify(opportunitySource, never()).findOpportunity(anyLong(), anyLong(), any());
        verify(proposalRepository, never()).append(any());
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
        verify(opportunitySource, never()).findOpportunity(anyLong(), anyLong(), any());
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

    private Companion persistedCompanion() {
        return Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
    }
}
