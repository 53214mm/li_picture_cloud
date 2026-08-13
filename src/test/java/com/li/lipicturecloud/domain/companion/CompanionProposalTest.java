package com.li.lipicturecloud.domain.companion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanionProposalTest {

    private static final Instant NOW = Instant.parse("2026-08-14T08:00:00Z");

    @Test
    void pendingProposalStartsWithoutGateResult() {
        CompanionProposal proposal = CompanionProposal.pending(11L, 7L,
                ProposalOpportunityType.WEEKLY_REVIEW, new BigDecimal("42.50"),
                "这周你喂了我 3 次。想听我讲一段我们的故事吗？", NOW);

        assertThat(proposal.status()).isEqualTo(ProposalStatus.PENDING);
        assertThat(proposal.gateResult()).isNull();
        assertThat(proposal.impulseScore()).isEqualByComparingTo("42.50");
        assertThat(proposal.revision()).isZero();
    }

    @Test
    void acceptIgnoreScoldAndExpireAreTerminal() {
        CompanionProposal pending = CompanionProposal.pending(11L, 7L,
                ProposalOpportunityType.WEEKLY_REVIEW, new BigDecimal("10.00"),
                "这周你喂了我 3 次。想听我讲一段我们的故事吗？", NOW);

        CompanionProposal done = pending.accept(NOW.plusSeconds(1));
        assertThat(done.status()).isEqualTo(ProposalStatus.DONE);
        assertThatThrownBy(() -> done.ignore(NOW)).isInstanceOf(IllegalStateException.class);

        CompanionProposal ignored = pending.ignore(NOW.plusSeconds(1));
        assertThat(ignored.status()).isEqualTo(ProposalStatus.IGNORED);

        CompanionProposal suppressed = pending.scold(NOW.plusSeconds(1));
        assertThat(suppressed.status()).isEqualTo(ProposalStatus.SUPPRESSED);
        assertThat(suppressed.gateResult()).isEqualTo("SCOLDED");

        CompanionProposal expired = pending.expire(NOW.plusSeconds(1));
        assertThat(expired.status()).isEqualTo(ProposalStatus.EXPIRED);
        assertThatThrownBy(() -> expired.accept(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnsafeOrOversizedContent() {
        assertThatThrownBy(() -> CompanionProposal.pending(11L, 7L,
                ProposalOpportunityType.WEEKLY_REVIEW, new BigDecimal("1.00"),
                "包含 http://example.com 的提案", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompanionProposal.pending(11L, 7L,
                ProposalOpportunityType.WEEKLY_REVIEW, new BigDecimal("1.00"),
                "长".repeat(501), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompanionProposal.pending(11L, 7L,
                ProposalOpportunityType.WEEKLY_REVIEW, new BigDecimal("100.01"),
                "正常文案", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompanionProposal.pending(11L, 7L,
                ProposalOpportunityType.WEEKLY_REVIEW, new BigDecimal("-0.01"),
                "正常文案", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withIdGuardsItsTransition() {
        CompanionProposal proposal = CompanionProposal.pending(11L, 7L,
                ProposalOpportunityType.WEEKLY_REVIEW, new BigDecimal("1.00"), "正常文案", NOW);

        assertThat(proposal.withId(51L).id()).isEqualTo(51L);
        assertThatThrownBy(() -> proposal.withId(0L)).isInstanceOf(IllegalStateException.class);
    }
}
