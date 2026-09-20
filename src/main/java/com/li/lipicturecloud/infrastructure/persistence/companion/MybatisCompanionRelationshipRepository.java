package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.li.lipicturecloud.domain.companion.CompanionRelationship;
import com.li.lipicturecloud.domain.companion.CompanionRelationshipRepository;
import com.li.lipicturecloud.mapper.CompanionRelationshipMapper;
import com.li.lipicturecloud.model.entity.CompanionRelationshipEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisCompanionRelationshipRepository implements CompanionRelationshipRepository {

    private final CompanionRelationshipMapper relationshipMapper;
    private final Clock clock;

    public MybatisCompanionRelationshipRepository(CompanionRelationshipMapper relationshipMapper,
                                                  Clock clock) {
        this.relationshipMapper = relationshipMapper;
        this.clock = clock;
    }

    @Override
    public Optional<CompanionRelationship> findByCompanionAndSubject(long companionId, long subjectId) {
        CompanionRelationshipEntity row = relationshipMapper.selectOne(
                new LambdaQueryWrapper<CompanionRelationshipEntity>()
                        .eq(CompanionRelationshipEntity::getCompanionId, companionId)
                        .eq(CompanionRelationshipEntity::getSubjectId, subjectId));
        return Optional.ofNullable(row).map(this::fromRow);
    }

    @Override
    public CompanionRelationship createIfAbsent(long companionId, long subjectId) {
        Optional<CompanionRelationship> existing = findByCompanionAndSubject(companionId, subjectId);
        if (existing.isPresent()) {
            return existing.get();
        }
        CompanionRelationshipEntity row = toRow(CompanionRelationship.initial(companionId, subjectId));
        try {
            relationshipMapper.insert(row);
        } catch (DuplicateKeyException raceWonElsewhere) {
            return findByCompanionAndSubject(companionId, subjectId).orElseThrow(() ->
                    new IllegalStateException("关系唯一键冲突后无法读取已有行", raceWonElsewhere));
        }
        return fromRow(row);
    }

    @Override
    public boolean save(CompanionRelationship after, long expectedRevision) {
        Objects.requireNonNull(after, "relationship");
        if (after.id() == null) {
            throw new IllegalArgumentException("cannot save an unpersisted relationship");
        }
        if (expectedRevision < 0 || after.revision() != Math.addExact(expectedRevision, 1L)) {
            throw new IllegalArgumentException("relationship revision must advance by exactly one");
        }
        UpdateWrapper<CompanionRelationshipEntity> update = new UpdateWrapper<>();
        update.eq("id", after.id())
                .eq("revision", expectedRevision)
                .set("familiarity", after.familiarity())
                .set("trust", after.trust())
                .set("closeness", after.closeness())
                .set("tacit", after.tacit())
                .set("recentFeedback", after.recentFeedback())
                .set("revision", after.revision())
                .set("updateTime", Date.from(clock.instant()));
        return relationshipMapper.update(null, update) == 1;
    }

    private CompanionRelationship fromRow(CompanionRelationshipEntity row) {
        return CompanionRelationship.restore(row.getId(), row.getCompanionId(), row.getSubjectId(),
                value(row.getFamiliarity()), value(row.getTrust()), value(row.getCloseness()),
                value(row.getTacit()), value(row.getRecentFeedback()),
                Objects.requireNonNull(row.getRevision(), "revision"));
    }

    private CompanionRelationshipEntity toRow(CompanionRelationship relationship) {
        CompanionRelationshipEntity row = new CompanionRelationshipEntity();
        row.setId(relationship.id());
        row.setCompanionId(relationship.companionId());
        row.setSubjectId(relationship.subjectId());
        row.setFamiliarity(relationship.familiarity());
        row.setTrust(relationship.trust());
        row.setCloseness(relationship.closeness());
        row.setTacit(relationship.tacit());
        row.setRecentFeedback(relationship.recentFeedback());
        row.setRevision(relationship.revision());
        // 显式对齐创建与更新时间，避免数据库默认时钟与 JVM 时钟的微小偏差。
        Date now = Date.from(clock.instant());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    private static BigDecimal value(BigDecimal stored) {
        return Objects.requireNonNull(stored, "relationship axis value");
    }
}
