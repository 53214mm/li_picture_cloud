package com.li.lipicturecloud.infrastructure.persistence.airuntime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.li.lipicturecloud.domain.airuntime.PlatformTrialLedger;
import com.li.lipicturecloud.domain.airuntime.PlatformTrialLedgerRepository;
import com.li.lipicturecloud.mapper.PlatformTrialLedgerMapper;
import com.li.lipicturecloud.model.entity.PlatformTrialLedgerEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisPlatformTrialLedgerRepository implements PlatformTrialLedgerRepository {

    private final PlatformTrialLedgerMapper ledgerMapper;
    private final Clock clock;

    public MybatisPlatformTrialLedgerRepository(PlatformTrialLedgerMapper ledgerMapper, Clock clock) {
        this.ledgerMapper = ledgerMapper;
        this.clock = clock;
    }

    @Override
    public Optional<PlatformTrialLedger> findBySubjectId(long subjectId) {
        if (subjectId <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(ledgerMapper.selectOne(
                        new LambdaQueryWrapper<PlatformTrialLedgerEntity>()
                                .eq(PlatformTrialLedgerEntity::getSubjectId, subjectId)))
                .map(this::fromRow);
    }

    @Override
    public PlatformTrialLedger insert(PlatformTrialLedger ledger) {
        Objects.requireNonNull(ledger, "ledger");
        if (ledger.id() != null) {
            throw new IllegalArgumentException("cannot insert an already persisted ledger");
        }
        PlatformTrialLedgerEntity row = toRow(ledger);
        try {
            ledgerMapper.insert(row);
        } catch (DuplicateKeyException raceWonElsewhere) {
            // subjectId 唯一键是最终仲裁；并发首建时输的一方读取赢家行。
            return findBySubjectId(ledger.subjectId())
                    .orElseThrow(() -> new IllegalStateException("试用账本唯一键冲突后无法读取已有行",
                            raceWonElsewhere));
        }
        return ledger.withId(Objects.requireNonNull(row.getId(), "assigned ledger id"));
    }

    @Override
    public boolean save(PlatformTrialLedger after, long expectedRevision) {
        Objects.requireNonNull(after, "ledger");
        if (after.id() == null) {
            throw new IllegalArgumentException("cannot save an unpersisted ledger");
        }
        if (expectedRevision < 0 || after.revision() != Math.addExact(expectedRevision, 1L)) {
            throw new IllegalArgumentException("ledger revision must advance by exactly one");
        }
        UpdateWrapper<PlatformTrialLedgerEntity> update = new UpdateWrapper<>();
        update.eq("id", after.id())
                .eq("revision", expectedRevision)
                .set("balance", after.balance())
                .set("reserved", after.reserved())
                .set("revision", after.revision())
                .set("updateTime", Date.from(clock.instant()));
        return ledgerMapper.update(null, update) == 1;
    }

    private PlatformTrialLedger fromRow(PlatformTrialLedgerEntity row) {
        return PlatformTrialLedger.restore(row.getId(), row.getSubjectId(), row.getBalance(),
                row.getReserved(), Objects.requireNonNull(row.getRevision(), "revision"));
    }

    private PlatformTrialLedgerEntity toRow(PlatformTrialLedger ledger) {
        PlatformTrialLedgerEntity row = new PlatformTrialLedgerEntity();
        row.setId(ledger.id());
        row.setSubjectId(ledger.subjectId());
        row.setBalance(ledger.balance());
        row.setReserved(ledger.reserved());
        row.setRevision(ledger.revision());
        Date now = Date.from(clock.instant());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }
}
