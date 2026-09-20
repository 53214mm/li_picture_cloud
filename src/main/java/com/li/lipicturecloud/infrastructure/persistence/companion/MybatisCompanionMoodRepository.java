package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.li.lipicturecloud.domain.companion.CompanionMood;
import com.li.lipicturecloud.domain.companion.CompanionMoodRepository;
import com.li.lipicturecloud.mapper.CompanionMoodMapper;
import com.li.lipicturecloud.model.entity.CompanionMoodEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisCompanionMoodRepository implements CompanionMoodRepository {

    private final CompanionMoodMapper moodMapper;

    public MybatisCompanionMoodRepository(CompanionMoodMapper moodMapper) {
        this.moodMapper = moodMapper;
    }

    @Override
    public Optional<CompanionMood> findByCompanionId(long companionId) {
        CompanionMoodEntity row = moodMapper.selectOne(new LambdaQueryWrapper<CompanionMoodEntity>()
                .eq(CompanionMoodEntity::getCompanionId, companionId));
        return Optional.ofNullable(row).map(this::fromRow);
    }

    @Override
    public CompanionMood insert(CompanionMood mood) {
        Objects.requireNonNull(mood, "mood");
        if (mood.id() != null) {
            throw new IllegalArgumentException("cannot insert an already persisted mood");
        }
        CompanionMoodEntity row = toRow(mood);
        try {
            moodMapper.insert(row);
        } catch (DuplicateKeyException raceWonElsewhere) {
            // companionId 唯一键是最终仲裁；并发创建时输的一方读取赢家行。
            return findByCompanionId(mood.companionId()).orElseThrow(() ->
                    new IllegalStateException("情绪唯一键冲突后无法读取已有行", raceWonElsewhere));
        }
        return mood.withId(Objects.requireNonNull(row.getId(), "assigned mood id"));
    }

    @Override
    public boolean save(CompanionMood after, long expectedRevision) {
        Objects.requireNonNull(after, "mood");
        if (after.id() == null) {
            throw new IllegalArgumentException("cannot save an unpersisted mood");
        }
        if (expectedRevision < 0 || after.revision() != Math.addExact(expectedRevision, 1L)) {
            throw new IllegalArgumentException("mood revision must advance by exactly one");
        }
        UpdateWrapper<CompanionMoodEntity> update = new UpdateWrapper<>();
        update.eq("id", after.id())
                .eq("revision", expectedRevision)
                .set("energy", after.energy())
                .set("joy", after.joy())
                .set("loneliness", after.loneliness())
                .set("inspiration", after.inspiration())
                .set("irritation", after.irritation())
                .set("revision", after.revision())
                .set("updateTime", Date.from(after.updatedAt()));
        return moodMapper.update(null, update) == 1;
    }

    private CompanionMood fromRow(CompanionMoodEntity row) {
        return CompanionMood.restore(row.getId(), row.getCompanionId(),
                value(row.getEnergy()), value(row.getJoy()), value(row.getLoneliness()),
                value(row.getInspiration()), value(row.getIrritation()),
                Objects.requireNonNull(row.getRevision(), "revision"),
                Objects.requireNonNull(row.getUpdateTime(), "updateTime").toInstant());
    }

    private CompanionMoodEntity toRow(CompanionMood mood) {
        CompanionMoodEntity row = new CompanionMoodEntity();
        row.setId(mood.id());
        row.setCompanionId(mood.companionId());
        row.setEnergy(mood.energy());
        row.setJoy(mood.joy());
        row.setLoneliness(mood.loneliness());
        row.setInspiration(mood.inspiration());
        row.setIrritation(mood.irritation());
        row.setRevision(mood.revision());
        // 显式对齐创建与更新时间，避免数据库默认时钟与 JVM 时钟的微小偏差。
        Date now = Date.from(mood.updatedAt());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    private static BigDecimal value(BigDecimal stored) {
        return Objects.requireNonNull(stored, "mood axis value");
    }
}
