package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.li.lipicturecloud.domain.companion.CompanionProposal;
import com.li.lipicturecloud.domain.companion.CompanionProposalRepository;
import com.li.lipicturecloud.domain.companion.ProposalOpportunityType;
import com.li.lipicturecloud.domain.companion.ProposalStatus;
import com.li.lipicturecloud.mapper.CompanionProposalMapper;
import com.li.lipicturecloud.model.entity.CompanionProposalEntity;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisCompanionProposalRepository implements CompanionProposalRepository {

    private final CompanionProposalMapper proposalMapper;

    public MybatisCompanionProposalRepository(CompanionProposalMapper proposalMapper) {
        this.proposalMapper = proposalMapper;
    }

    @Override
    public CompanionProposal append(CompanionProposal proposal) {
        Objects.requireNonNull(proposal, "proposal");
        if (proposal.id() != null) {
            throw new IllegalArgumentException("cannot append an already persisted proposal");
        }
        CompanionProposalEntity row = toRow(proposal);
        proposalMapper.insert(row);
        return proposal.withId(Objects.requireNonNull(row.getId(), "assigned proposal id"));
    }

    @Override
    public Optional<CompanionProposal> findById(long id) {
        if (id <= 0) {
            return Optional.empty();
        }
        CompanionProposalEntity row = proposalMapper.selectById(id);
        return Optional.ofNullable(row).map(this::fromRow);
    }

    @Override
    public List<CompanionProposal> findActive(long companionId, int limit) {
        return proposalMapper.selectList(new LambdaQueryWrapper<CompanionProposalEntity>()
                        .eq(CompanionProposalEntity::getCompanionId, companionId)
                        .eq(CompanionProposalEntity::getStatus, ProposalStatus.PENDING.name())
                        .orderByDesc(CompanionProposalEntity::getCreateTime)
                        .orderByDesc(CompanionProposalEntity::getId)
                        .last("LIMIT " + boundedLimit(limit)))
                .stream().map(this::fromRow).toList();
    }

    @Override
    public List<CompanionProposal> findRecent(long companionId, int limit) {
        return proposalMapper.selectList(new LambdaQueryWrapper<CompanionProposalEntity>()
                        .eq(CompanionProposalEntity::getCompanionId, companionId)
                        .orderByDesc(CompanionProposalEntity::getCreateTime)
                        .orderByDesc(CompanionProposalEntity::getId)
                        .last("LIMIT " + boundedLimit(limit)))
                .stream().map(this::fromRow).toList();
    }

    @Override
    public boolean save(CompanionProposal after, long expectedRevision) {
        Objects.requireNonNull(after, "proposal");
        if (after.id() == null) {
            throw new IllegalArgumentException("cannot save an unpersisted proposal");
        }
        if (expectedRevision < 0 || after.revision() != Math.addExact(expectedRevision, 1L)) {
            throw new IllegalArgumentException("proposal revision must advance by exactly one");
        }
        UpdateWrapper<CompanionProposalEntity> update = new UpdateWrapper<>();
        update.eq("id", after.id())
                .eq("revision", expectedRevision)
                .set("status", after.status().name())
                .set("gateResult", after.gateResult())
                .set("revision", after.revision())
                .set("updateTime", Date.from(after.updatedTime()));
        return proposalMapper.update(null, update) == 1;
    }

    private CompanionProposal fromRow(CompanionProposalEntity row) {
        return CompanionProposal.restore(row.getId(), row.getCompanionId(), row.getSubjectId(),
                ProposalOpportunityType.valueOf(row.getOpportunityType()),
                value(row.getImpulseScore()), row.getContent(),
                ProposalStatus.valueOf(row.getStatus()), row.getGateResult(),
                Objects.requireNonNull(row.getRevision(), "revision"),
                Objects.requireNonNull(row.getCreateTime(), "createTime").toInstant(),
                Objects.requireNonNull(row.getUpdateTime(), "updateTime").toInstant());
    }

    private CompanionProposalEntity toRow(CompanionProposal proposal) {
        CompanionProposalEntity row = new CompanionProposalEntity();
        row.setId(proposal.id());
        row.setCompanionId(proposal.companionId());
        row.setSubjectId(proposal.subjectId());
        row.setOpportunityType(proposal.opportunityType().name());
        row.setImpulseScore(proposal.impulseScore());
        row.setContent(proposal.content());
        row.setStatus(proposal.status().name());
        row.setGateResult(proposal.gateResult());
        row.setRevision(proposal.revision());
        row.setCreateTime(Date.from(proposal.createdTime()));
        row.setUpdateTime(Date.from(proposal.updatedTime()));
        return row;
    }

    private static BigDecimal value(BigDecimal stored) {
        return Objects.requireNonNull(stored, "impulseScore");
    }

    private static int boundedLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }
}
