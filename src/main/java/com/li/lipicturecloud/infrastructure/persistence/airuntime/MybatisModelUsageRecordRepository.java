package com.li.lipicturecloud.infrastructure.persistence.airuntime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.domain.airuntime.ModelUsageRecord;
import com.li.lipicturecloud.domain.airuntime.ModelUsageRecordRepository;
import com.li.lipicturecloud.mapper.ModelUsageRecordMapper;
import com.li.lipicturecloud.model.entity.ModelUsageRecordEntity;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisModelUsageRecordRepository implements ModelUsageRecordRepository {

    private final ModelUsageRecordMapper usageMapper;

    public MybatisModelUsageRecordRepository(ModelUsageRecordMapper usageMapper) {
        this.usageMapper = usageMapper;
    }

    @Override
    public ModelUsageRecord append(ModelUsageRecord record) {
        Objects.requireNonNull(record, "record");
        if (record.id() != null) {
            throw new IllegalArgumentException("cannot append an already persisted usage record");
        }
        ModelUsageRecordEntity row = new ModelUsageRecordEntity();
        row.setSubjectId(record.subjectId());
        row.setTask(record.task().name());
        row.setConnectionId(record.connectionId());
        row.setProvider(record.provider().name());
        row.setModelCode(record.modelCode());
        row.setCostSource(record.costSource().name());
        row.setSuccess(record.success());
        row.setSafeErrorCode(record.safeErrorCode());
        row.setCorrelationId(record.correlationId());
        row.setCreatedTime(Date.from(record.createdTime()));
        usageMapper.insert(row);
        return record.withId(Objects.requireNonNull(row.getId(), "assigned usage record id"));
    }

    @Override
    public Optional<ModelUsageRecord> findById(long id) {
        if (id <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(usageMapper.selectById(id)).map(this::fromRow);
    }

    @Override
    public List<ModelUsageRecord> findRecent(long subjectId, int limit) {
        return usageMapper.selectList(new LambdaQueryWrapper<ModelUsageRecordEntity>()
                        .eq(ModelUsageRecordEntity::getSubjectId, subjectId)
                        .orderByDesc(ModelUsageRecordEntity::getCreatedTime)
                        .orderByDesc(ModelUsageRecordEntity::getId)
                        .last("LIMIT " + boundedLimit(limit)))
                .stream().map(this::fromRow).toList();
    }

    private ModelUsageRecord fromRow(ModelUsageRecordEntity row) {
        return new ModelUsageRecord(row.getId(), row.getSubjectId(),
                ModelTask.valueOf(row.getTask()), row.getConnectionId(),
                ModelProvider.valueOf(row.getProvider()), row.getModelCode(),
                CostSource.valueOf(row.getCostSource()), Boolean.TRUE.equals(row.getSuccess()),
                row.getSafeErrorCode(), row.getCorrelationId(),
                Objects.requireNonNull(row.getCreatedTime(), "createdTime").toInstant());
    }

    private static int boundedLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }
}
