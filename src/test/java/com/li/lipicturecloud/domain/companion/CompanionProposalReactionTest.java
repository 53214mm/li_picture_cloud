package com.li.lipicturecloud.domain.companion;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanionProposalReactionTest {

    private static final Instant NOW = Instant.parse("2026-08-14T08:00:00Z");

    @Test
    void buildsAReactionWithTypeAndTime() {
        CompanionProposalReaction reaction = CompanionProposalReaction.of(61L, 7L,
                ProposalReactionType.SCOLD, NOW);

        assertThat(reaction.proposalId()).isEqualTo(61L);
        assertThat(reaction.subjectId()).isEqualTo(7L);
        assertThat(reaction.reactionType()).isEqualTo(ProposalReactionType.SCOLD);
        assertThat(reaction.createdTime()).isEqualTo(NOW);
        assertThat(reaction.id()).isNull();
    }

    @Test
    void rejectsInvalidIdentityAndNulls() {
        assertThatThrownBy(() -> CompanionProposalReaction.of(0L, 7L,
                ProposalReactionType.ACCEPT, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompanionProposalReaction.of(61L, 0L,
                ProposalReactionType.IGNORE, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompanionProposalReaction.of(61L, 7L, null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CompanionProposalReaction.of(61L, 7L,
                ProposalReactionType.ACCEPT, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CompanionProposalReaction(-1L, 61L, 7L,
                ProposalReactionType.ACCEPT, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
