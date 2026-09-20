package com.li.lipicturecloud.domain.companion;

import java.time.Instant;

/**
 * 用户对提案的追加式反馈。
 */
public record CompanionProposalReaction(
        Long id,
        long proposalId,
        long subjectId,
        ProposalReactionType reactionType,
        Instant createdTime) {

    public CompanionProposalReaction {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (proposalId <= 0 || subjectId <= 0) {
            throw new IllegalArgumentException("invalid reaction identity");
        }
        java.util.Objects.requireNonNull(reactionType, "reactionType");
        java.util.Objects.requireNonNull(createdTime, "createdTime");
    }

    public static CompanionProposalReaction of(long proposalId, long subjectId,
                                               ProposalReactionType reactionType, Instant now) {
        return new CompanionProposalReaction(null, proposalId, subjectId, reactionType, now);
    }
}
