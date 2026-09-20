package com.li.lipicturecloud.infrastructure.persistence.airuntime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.domain.airuntime.CreationKind;
import com.li.lipicturecloud.domain.airuntime.CreationStatus;
import com.li.lipicturecloud.domain.airuntime.CreationTask;
import com.li.lipicturecloud.domain.airuntime.CreationTaskRepository;
import com.li.lipicturecloud.mapper.CreationTaskMapper;
import com.li.lipicturecloud.model.entity.CreationTaskEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisCreationTaskRepository implements CreationTaskRepository {

    private final CreationTaskMapper taskMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MybatisCreationTaskRepository(CreationTaskMapper taskMapper, ObjectMapper objectMapper,
                                         Clock clock) {
        this.taskMapper = taskMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public Optional<CreationTask> findBySubjectAndKey(long subjectId, String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        return Optional.ofNullable(taskMapper.selectOne(
                        new LambdaQueryWrapper<CreationTaskEntity>()
                                .eq(CreationTaskEntity::getSubjectId, subjectId)
                                .eq(CreationTaskEntity::getIdempotencyKey, idempotencyKey)))
                .map(this::fromRow);
    }

    @Override
    public Optional<CreationTask> findById(long id) {
        if (id <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(taskMapper.selectById(id)).map(this::fromRow);
    }

    @Override
    public List<CreationTask> findBySubjectIdAndKind(long subjectId, CreationKind kind, int limit) {
        Objects.requireNonNull(kind, "kind");
        return taskMapper.selectList(new LambdaQueryWrapper<CreationTaskEntity>()
                        .eq(CreationTaskEntity::getSubjectId, subjectId)
                        .eq(CreationTaskEntity::getKind, kind.name())
                        .orderByDesc(CreationTaskEntity::getUpdateTime)
                        .orderByDesc(CreationTaskEntity::getId)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 100))))
                .stream().map(this::fromRow).toList();
    }

    @Override
    public CreationTask insert(CreationTask task) {
        Objects.requireNonNull(task, "task");
        if (task.id() != null) {
            throw new IllegalArgumentException("cannot insert an already persisted creation task");
        }
        CreationTaskEntity row = toRow(task);
        try {
            taskMapper.insert(row);
        } catch (DuplicateKeyException raceWonElsewhere) {
            // (subjectId, idempotencyKey) 唯一键是最终仲裁；并发创建时输的一方读取赢家行。
            return findBySubjectAndKey(task.subjectId(), task.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException("创作任务唯一键冲突后无法读取已有行",
                            raceWonElsewhere));
        }
        return task.withId(Objects.requireNonNull(row.getId(), "assigned creation task id"));
    }

    @Override
    public boolean save(CreationTask after, long expectedRevision) {
        Objects.requireNonNull(after, "task");
        if (after.id() == null) {
            throw new IllegalArgumentException("cannot save an unpersisted creation task");
        }
        if (expectedRevision < 0 || after.revision() != Math.addExact(expectedRevision, 1L)) {
            throw new IllegalArgumentException("creation task revision must advance by exactly one");
        }
        UpdateWrapper<CreationTaskEntity> update = new UpdateWrapper<>();
        update.eq("id", after.id())
                .eq("revision", expectedRevision)
                .set("status", after.status().name())
                .set("outlineText", after.outlineText())
                .set("draftText", after.draftText())
                .set("resultText", after.resultText())
                .set("modelConnectionId", after.modelConnectionId())
                .set("revision", after.revision())
                .set("updateTime", Date.from(after.updatedTime()));
        return taskMapper.update(null, update) == 1;
    }

    private CreationTask fromRow(CreationTaskEntity row) {
        return new CreationTask(row.getId(), row.getSubjectId(),
                CreationKind.valueOf(row.getKind()), parsePictureIds(row.getSourcePictureIds()),
                CreationStatus.valueOf(row.getStatus()), row.getOutlineText(), row.getDraftText(),
                row.getResultText(), row.getModelConnectionId(), row.getIdempotencyKey(),
                Objects.requireNonNull(row.getRevision(), "revision"),
                Objects.requireNonNull(row.getCreateTime(), "createTime").toInstant(),
                Objects.requireNonNull(row.getUpdateTime(), "updateTime").toInstant());
    }

    private CreationTaskEntity toRow(CreationTask task) {
        CreationTaskEntity row = new CreationTaskEntity();
        row.setId(task.id());
        row.setSubjectId(task.subjectId());
        row.setKind(task.kind().name());
        row.setSourcePictureIds(serializePictureIds(task.sourcePictureIds()));
        row.setStatus(task.status().name());
        row.setOutlineText(task.outlineText());
        row.setDraftText(task.draftText());
        row.setResultText(task.resultText());
        row.setModelConnectionId(task.modelConnectionId());
        row.setIdempotencyKey(task.idempotencyKey());
        row.setRevision(task.revision());
        row.setCreateTime(Date.from(task.createdTime()));
        row.setUpdateTime(Date.from(task.updatedTime()));
        return row;
    }

    private List<Long> parsePictureIds(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() { });
        } catch (Exception malformed) {
            throw new IllegalStateException("stored source picture ids are malformed", malformed);
        }
    }

    private String serializePictureIds(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception failure) {
            throw new IllegalStateException("cannot serialize source picture ids", failure);
        }
    }
}
