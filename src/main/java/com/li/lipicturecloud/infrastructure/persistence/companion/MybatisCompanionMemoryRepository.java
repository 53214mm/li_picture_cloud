package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.li.lipicturecloud.domain.companion.CompanionMemory;
import com.li.lipicturecloud.domain.companion.CompanionMemoryRepository;
import com.li.lipicturecloud.domain.companion.MemorySourceType;
import com.li.lipicturecloud.domain.companion.MemoryStatus;
import com.li.lipicturecloud.mapper.CompanionMemoryMapper;
import com.li.lipicturecloud.model.entity.CompanionMemoryEntity;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisCompanionMemoryRepository implements CompanionMemoryRepository {

    private static final List<String> ACTIVE_STATUSES = List.of(
            MemoryStatus.PENDING.name(), MemoryStatus.CONFIRMED.name(), MemoryStatus.DISMISSED.name());

    private final CompanionMemoryMapper memoryMapper;

    public MybatisCompanionMemoryRepository(CompanionMemoryMapper memoryMapper) {
        this.memoryMapper = memoryMapper;
    }

    @Override
    public CompanionMemory append(CompanionMemory memory) {
        Objects.requireNonNull(memory, "memory");
        if (memory.id() != null) {
            throw new IllegalArgumentException("cannot append an already persisted memory");
        }
        CompanionMemoryEntity row = toRow(memory);
        memoryMapper.insert(row);
        return memory.withId(Objects.requireNonNull(row.getId(), "assigned memory id"));
    }

    @Override
    public Optional<CompanionMemory> findById(long id) {
        if (id <= 0) {
            return Optional.empty();
        }
        CompanionMemoryEntity row = memoryMapper.selectById(id);
        return Optional.ofNullable(row).map(this::fromRow);
    }

    @Override
    public List<CompanionMemory> findRecent(long companionId, int limit) {
        return memoryMapper.selectList(new LambdaQueryWrapper<CompanionMemoryEntity>()
                        .eq(CompanionMemoryEntity::getCompanionId, companionId)
                        .orderByDesc(CompanionMemoryEntity::getCreateTime)
                        .orderByDesc(CompanionMemoryEntity::getId)
                        .last("LIMIT " + boundedLimit(limit)))
                .stream().map(this::fromRow).toList();
    }

    @Override
    public List<CompanionMemory> findActive(long companionId, int limit) {
        return memoryMapper.selectList(new LambdaQueryWrapper<CompanionMemoryEntity>()
                        .eq(CompanionMemoryEntity::getCompanionId, companionId)
                        .in(CompanionMemoryEntity::getStatus, ACTIVE_STATUSES)
                        .orderByDesc(CompanionMemoryEntity::getCreateTime)
                        .orderByDesc(CompanionMemoryEntity::getId)
                        .last("LIMIT " + boundedLimit(limit)))
                .stream().map(this::fromRow).toList();
    }

    @Override
    public boolean save(CompanionMemory after, long expectedRevision) {
        Objects.requireNonNull(after, "memory");
        if (after.id() == null) {
            throw new IllegalArgumentException("cannot save an unpersisted memory");
        }
        if (expectedRevision < 0 || after.revision() != Math.addExact(expectedRevision, 1L)) {
            throw new IllegalArgumentException("memory revision must advance by exactly one");
        }
        UpdateWrapper<CompanionMemoryEntity> update = new UpdateWrapper<>();
        update.eq("id", after.id())
                .eq("revision", expectedRevision)
                .set("content", after.content())
                .set("status", after.status().name())
                .set("invalidatedReason", after.invalidatedReason())
                .set("revision", after.revision())
                .set("updateTime", Date.from(after.updatedTime()));
        return memoryMapper.update(null, update) == 1;
    }

    private CompanionMemory fromRow(CompanionMemoryEntity row) {
        return CompanionMemory.restore(row.getId(), row.getCompanionId(), row.getSubjectId(),
                row.getPictureId(), row.getGrowthRecordId(),
                MemorySourceType.valueOf(row.getSourceType()), row.getContent(), row.getOriginalContent(),
                value(row.getConfidence()), MemoryStatus.valueOf(row.getStatus()),
                row.getInvalidatedReason(), Objects.requireNonNull(row.getRevision(), "revision"),
                Objects.requireNonNull(row.getCreateTime(), "createTime").toInstant(),
                Objects.requireNonNull(row.getUpdateTime(), "updateTime").toInstant());
    }

    private CompanionMemoryEntity toRow(CompanionMemory memory) {
        CompanionMemoryEntity row = new CompanionMemoryEntity();
        row.setId(memory.id());
        row.setCompanionId(memory.companionId());
        row.setSubjectId(memory.subjectId());
        row.setPictureId(memory.pictureId());
        row.setGrowthRecordId(memory.growthRecordId());
        row.setSourceType(memory.sourceType().name());
        row.setContent(memory.content());
        row.setOriginalContent(memory.originalContent());
        row.setConfidence(memory.confidence());
        row.setStatus(memory.status().name());
        row.setInvalidatedReason(memory.invalidatedReason());
        row.setRevision(memory.revision());
        // 显式对齐创建与更新时间，避免数据库默认时钟与 JVM 时钟的微小偏差。
        row.setCreateTime(Date.from(memory.createdTime()));
        row.setUpdateTime(Date.from(memory.updatedTime()));
        return row;
    }

    private static int boundedLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }

    private static BigDecimal value(BigDecimal stored) {
        return Objects.requireNonNull(stored, "memory confidence");
    }
}
