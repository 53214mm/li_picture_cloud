package com.li.lipicturecloud.infrastructure.persistence.airuntime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelConnectionRepository;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.mapper.ModelConnectionMapper;
import com.li.lipicturecloud.model.entity.ModelConnectionEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisModelConnectionRepository implements ModelConnectionRepository {

    private final ModelConnectionMapper connectionMapper;
    private final Clock clock;

    public MybatisModelConnectionRepository(ModelConnectionMapper connectionMapper, Clock clock) {
        this.connectionMapper = connectionMapper;
        this.clock = clock;
    }

    @Override
    public Optional<ModelConnection> findById(long id) {
        if (id <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(connectionMapper.selectById(id)).map(this::fromRow);
    }

    @Override
    public List<ModelConnection> findByOwnerId(long subjectId) {
        return connectionMapper.selectList(new LambdaQueryWrapper<ModelConnectionEntity>()
                        .eq(ModelConnectionEntity::getSubjectId, subjectId)
                        .orderByAsc(ModelConnectionEntity::getId))
                .stream().map(this::fromRow).toList();
    }

    @Override
    public ModelConnection insert(ModelConnection connection) {
        Objects.requireNonNull(connection, "connection");
        if (connection.id() != null) {
            throw new IllegalArgumentException("cannot insert an already persisted connection");
        }
        ModelConnectionEntity row = toRow(connection);
        try {
            connectionMapper.insert(row);
        } catch (DuplicateKeyException raceWonElsewhere) {
            // (subjectId, displayName) 唯一键是最终仲裁；并发创建时输的一方读取赢家行。
            return findByOwnerId(connection.subjectId()).stream()
                    .filter(existing -> existing.displayName().equals(connection.displayName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("连接唯一键冲突后无法读取已有行",
                            raceWonElsewhere));
        }
        return connection.withId(Objects.requireNonNull(row.getId(), "assigned connection id"));
    }

    @Override
    public boolean save(ModelConnection after, long expectedRevision) {
        Objects.requireNonNull(after, "connection");
        if (after.id() == null) {
            throw new IllegalArgumentException("cannot save an unpersisted connection");
        }
        if (expectedRevision < 0 || after.revision() != Math.addExact(expectedRevision, 1L)) {
            throw new IllegalArgumentException("connection revision must advance by exactly one");
        }
        UpdateWrapper<ModelConnectionEntity> update = new UpdateWrapper<>();
        update.eq("id", after.id())
                .eq("revision", expectedRevision)
                .set("credentialId", after.credentialId())
                .set("enabled", after.enabled())
                .set("revision", after.revision())
                .set("updateTime", Date.from(clock.instant()));
        return connectionMapper.update(null, update) == 1;
    }

    @Override
    public boolean delete(long id, long expectedRevision) {
        if (id <= 0 || expectedRevision < 0) {
            return false;
        }
        return connectionMapper.delete(new LambdaQueryWrapper<ModelConnectionEntity>()
                .eq(ModelConnectionEntity::getId, id)
                .eq(ModelConnectionEntity::getRevision, expectedRevision)) == 1;
    }

    private ModelConnection fromRow(ModelConnectionEntity row) {
        return ModelConnection.restore(row.getId(), row.getSubjectId(),
                ModelProvider.valueOf(row.getProvider()), row.getDisplayName(),
                URI.create(row.getEndpointUri()), row.getModelCode(), row.getCredentialId(),
                Boolean.TRUE.equals(row.getEnabled()),
                Objects.requireNonNull(row.getRevision(), "revision"));
    }

    private ModelConnectionEntity toRow(ModelConnection connection) {
        ModelConnectionEntity row = new ModelConnectionEntity();
        row.setId(connection.id());
        row.setSubjectId(connection.subjectId());
        row.setProvider(connection.provider().name());
        row.setDisplayName(connection.displayName());
        row.setEndpointUri(connection.endpointUri().toString());
        row.setModelCode(connection.modelCode());
        row.setCredentialId(connection.credentialId());
        row.setEnabled(connection.enabled());
        row.setRevision(connection.revision());
        // 显式对齐创建与更新时间，避免数据库默认时钟与 JVM 时钟的微小偏差。
        Date now = Date.from(clock.instant());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }
}
