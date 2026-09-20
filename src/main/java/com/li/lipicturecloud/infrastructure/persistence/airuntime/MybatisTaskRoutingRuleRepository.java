package com.li.lipicturecloud.infrastructure.persistence.airuntime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRule;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRuleRepository;
import com.li.lipicturecloud.mapper.TaskRoutingRuleMapper;
import com.li.lipicturecloud.model.entity.TaskRoutingRuleEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisTaskRoutingRuleRepository implements TaskRoutingRuleRepository {

    private final TaskRoutingRuleMapper ruleMapper;
    private final Clock clock;

    public MybatisTaskRoutingRuleRepository(TaskRoutingRuleMapper ruleMapper, Clock clock) {
        this.ruleMapper = ruleMapper;
        this.clock = clock;
    }

    @Override
    public Optional<TaskRoutingRule> findBySubjectAndTask(long subjectId, ModelTask task) {
        Objects.requireNonNull(task, "task");
        return Optional.ofNullable(ruleMapper.selectOne(
                        new LambdaQueryWrapper<TaskRoutingRuleEntity>()
                                .eq(TaskRoutingRuleEntity::getSubjectId, subjectId)
                                .eq(TaskRoutingRuleEntity::getTask, task.name())))
                .map(this::fromRow);
    }

    @Override
    public List<TaskRoutingRule> findByOwnerId(long subjectId) {
        return ruleMapper.selectList(new LambdaQueryWrapper<TaskRoutingRuleEntity>()
                        .eq(TaskRoutingRuleEntity::getSubjectId, subjectId)
                        .orderByAsc(TaskRoutingRuleEntity::getId))
                .stream().map(this::fromRow).toList();
    }

    @Override
    public TaskRoutingRule insert(TaskRoutingRule rule) {
        Objects.requireNonNull(rule, "rule");
        if (rule.id() != null) {
            throw new IllegalArgumentException("cannot insert an already persisted routing rule");
        }
        TaskRoutingRuleEntity row = toRow(rule);
        try {
            ruleMapper.insert(row);
        } catch (DuplicateKeyException raceWonElsewhere) {
            // (subjectId, task) 唯一键是最终仲裁；并发创建时输的一方读取赢家行。
            return findBySubjectAndTask(rule.subjectId(), rule.task())
                    .orElseThrow(() -> new IllegalStateException("路由规则唯一键冲突后无法读取已有行",
                            raceWonElsewhere));
        }
        return rule.withId(Objects.requireNonNull(row.getId(), "assigned routing rule id"));
    }

    @Override
    public boolean save(TaskRoutingRule after, long expectedRevision) {
        Objects.requireNonNull(after, "rule");
        if (after.id() == null) {
            throw new IllegalArgumentException("cannot save an unpersisted routing rule");
        }
        if (expectedRevision < 0 || after.revision() != Math.addExact(expectedRevision, 1L)) {
            throw new IllegalArgumentException("routing revision must advance by exactly one");
        }
        UpdateWrapper<TaskRoutingRuleEntity> update = new UpdateWrapper<>();
        update.eq("id", after.id())
                .eq("revision", expectedRevision)
                .set("connectionId", after.connectionId())
                .set("revision", after.revision())
                .set("updateTime", Date.from(clock.instant()));
        return ruleMapper.update(null, update) == 1;
    }

    @Override
    public boolean delete(long id, long expectedRevision) {
        if (id <= 0 || expectedRevision < 0) {
            return false;
        }
        return ruleMapper.delete(new LambdaQueryWrapper<TaskRoutingRuleEntity>()
                .eq(TaskRoutingRuleEntity::getId, id)
                .eq(TaskRoutingRuleEntity::getRevision, expectedRevision)) == 1;
    }

    private TaskRoutingRule fromRow(TaskRoutingRuleEntity row) {
        return TaskRoutingRule.restore(row.getId(), row.getSubjectId(),
                ModelTask.valueOf(row.getTask()), row.getConnectionId(),
                Objects.requireNonNull(row.getRevision(), "revision"));
    }

    private TaskRoutingRuleEntity toRow(TaskRoutingRule rule) {
        TaskRoutingRuleEntity row = new TaskRoutingRuleEntity();
        row.setId(rule.id());
        row.setSubjectId(rule.subjectId());
        row.setTask(rule.task().name());
        row.setConnectionId(rule.connectionId());
        row.setRevision(rule.revision());
        Date now = Date.from(clock.instant());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }
}
