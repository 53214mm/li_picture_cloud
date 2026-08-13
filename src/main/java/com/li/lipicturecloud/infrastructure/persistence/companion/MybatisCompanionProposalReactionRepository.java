package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.li.lipicturecloud.domain.companion.CompanionProposalReaction;
import com.li.lipicturecloud.domain.companion.CompanionProposalReactionRepository;
import com.li.lipicturecloud.domain.companion.ProposalReactionType;
import com.li.lipicturecloud.mapper.CompanionProposalReactionMapper;
import com.li.lipicturecloud.model.entity.CompanionProposalReactionEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Date;
import java.util.Objects;

@Repository
public class MybatisCompanionProposalReactionRepository implements CompanionProposalReactionRepository {

    private final CompanionProposalReactionMapper reactionMapper;

    public MybatisCompanionProposalReactionRepository(CompanionProposalReactionMapper reactionMapper) {
        this.reactionMapper = reactionMapper;
    }

    @Override
    public CompanionProposalReaction append(CompanionProposalReaction reaction) {
        Objects.requireNonNull(reaction, "reaction");
        if (reaction.id() != null) {
            throw new IllegalArgumentException("cannot append an already persisted reaction");
        }
        CompanionProposalReactionEntity row = new CompanionProposalReactionEntity();
        row.setProposalId(reaction.proposalId());
        row.setSubjectId(reaction.subjectId());
        row.setReactionType(reaction.reactionType().name());
        row.setCreateTime(Date.from(reaction.createdTime()));
        reactionMapper.insert(row);
        return new CompanionProposalReaction(
                Objects.requireNonNull(row.getId(), "assigned reaction id"),
                reaction.proposalId(), reaction.subjectId(), reaction.reactionType(), reaction.createdTime());
    }

    @Override
    public long countScoldsSince(long subjectId, Instant since) {
        return reactionMapper.countScoldsSince(subjectId, Date.from(since));
    }
}
