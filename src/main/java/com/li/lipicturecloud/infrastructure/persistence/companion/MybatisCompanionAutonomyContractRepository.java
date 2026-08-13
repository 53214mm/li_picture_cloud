package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.li.lipicturecloud.domain.companion.CompanionAutonomyContract;
import com.li.lipicturecloud.domain.companion.CompanionAutonomyContractRepository;
import com.li.lipicturecloud.mapper.CompanionAutonomyContractMapper;
import com.li.lipicturecloud.model.entity.CompanionAutonomyContractEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.Clock;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisCompanionAutonomyContractRepository implements CompanionAutonomyContractRepository {

    private final CompanionAutonomyContractMapper contractMapper;
    private final Clock clock;

    public MybatisCompanionAutonomyContractRepository(CompanionAutonomyContractMapper contractMapper,
                                                      Clock clock) {
        this.contractMapper = contractMapper;
        this.clock = clock;
    }

    @Override
    public Optional<CompanionAutonomyContract> findByCompanionAndSubject(long companionId, long subjectId) {
        CompanionAutonomyContractEntity row = contractMapper.selectOne(
                new LambdaQueryWrapper<CompanionAutonomyContractEntity>()
                        .eq(CompanionAutonomyContractEntity::getCompanionId, companionId)
                        .eq(CompanionAutonomyContractEntity::getSubjectId, subjectId));
        return Optional.ofNullable(row).map(this::fromRow);
    }

    @Override
    public CompanionAutonomyContract createIfAbsent(long companionId, long subjectId) {
        Optional<CompanionAutonomyContract> existing = findByCompanionAndSubject(companionId, subjectId);
        if (existing.isPresent()) {
            return existing.get();
        }
        CompanionAutonomyContractEntity row = toRow(CompanionAutonomyContract.initial(companionId, subjectId));
        try {
            contractMapper.insert(row);
        } catch (DuplicateKeyException raceWonElsewhere) {
            return findByCompanionAndSubject(companionId, subjectId).orElseThrow(() ->
                    new IllegalStateException("契约唯一键冲突后无法读取已有行", raceWonElsewhere));
        }
        return fromRow(row);
    }

    @Override
    public boolean save(CompanionAutonomyContract after, long expectedRevision) {
        Objects.requireNonNull(after, "contract");
        if (after.id() == null) {
            throw new IllegalArgumentException("cannot save an unpersisted contract");
        }
        if (expectedRevision < 0 || after.revision() != Math.addExact(expectedRevision, 1L)) {
            throw new IllegalArgumentException("contract revision must advance by exactly one");
        }
        UpdateWrapper<CompanionAutonomyContractEntity> update = new UpdateWrapper<>();
        update.eq("id", after.id())
                .eq("revision", expectedRevision)
                .set("active", after.active())
                .set("quietStart", Time.valueOf(after.quietStart()))
                .set("quietEnd", Time.valueOf(after.quietEnd()))
                .set("maxFrequencyHours", after.maxFrequencyHours())
                .set("revision", after.revision())
                .set("updateTime", Date.from(clock.instant()));
        return contractMapper.update(null, update) == 1;
    }

    private CompanionAutonomyContract fromRow(CompanionAutonomyContractEntity row) {
        return CompanionAutonomyContract.restore(row.getId(), row.getCompanionId(), row.getSubjectId(),
                Boolean.TRUE.equals(row.getActive()),
                Objects.requireNonNull(row.getQuietStart(), "quietStart").toLocalTime(),
                Objects.requireNonNull(row.getQuietEnd(), "quietEnd").toLocalTime(),
                Objects.requireNonNull(row.getMaxFrequencyHours(), "maxFrequencyHours"),
                Objects.requireNonNull(row.getRevision(), "revision"));
    }

    private CompanionAutonomyContractEntity toRow(CompanionAutonomyContract contract) {
        CompanionAutonomyContractEntity row = new CompanionAutonomyContractEntity();
        row.setId(contract.id());
        row.setCompanionId(contract.companionId());
        row.setSubjectId(contract.subjectId());
        row.setActive(contract.active());
        row.setQuietStart(Time.valueOf(contract.quietStart()));
        row.setQuietEnd(Time.valueOf(contract.quietEnd()));
        row.setMaxFrequencyHours(contract.maxFrequencyHours());
        row.setRevision(contract.revision());
        Date now = Date.from(clock.instant());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }
}
