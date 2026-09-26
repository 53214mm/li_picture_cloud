package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.li.lipicturecloud.domain.companion.FeedingRun;
import com.li.lipicturecloud.domain.companion.FeedingRunRepository;
import com.li.lipicturecloud.domain.companion.FeedingRunStatus;
import com.li.lipicturecloud.domain.companion.NutritionMode;
import com.li.lipicturecloud.domain.companion.NutritionPolicy;
import com.li.lipicturecloud.mapper.CompanionFeedRunMapper;
import com.li.lipicturecloud.model.entity.CompanionFeedRunEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

/**
 * 喂养执行记录仓储的 MyBatis 实现。
 *
 * <p>一条 FeedingRun 表示一次带幂等键的喂养尝试。状态变更同时校验旧状态和 revision，
 * 防止重试请求或并发请求把更新后的结果覆盖掉。</p>
 */
@Repository
public class MybatisFeedingRunRepository implements FeedingRunRepository {

    private final CompanionFeedRunMapper feedRunMapper;

    public MybatisFeedingRunRepository(CompanionFeedRunMapper feedRunMapper) {
        this.feedRunMapper = feedRunMapper;
    }

    /** 按“伙伴 + 幂等键”查找已有执行，用来识别同一次客户端意图。 */
    @Override
    public Optional<FeedingRun> findByKey(long companionId, String idempotencyKey) {
        return Optional.ofNullable(feedRunMapper.selectOne(
                new LambdaQueryWrapper<CompanionFeedRunEntity>()
                        .eq(CompanionFeedRunEntity::getCompanionId, companionId)
                        .eq(CompanionFeedRunEntity::getIdempotencyKey, idempotencyKey)))
                .map(this::fromRow);
    }

    /** 插入新的 PROCESSING 执行；重复幂等键由数据库唯一约束拦截。 */
    @Override
    public FeedingRun insert(FeedingRun run) {
        Objects.requireNonNull(run, "feeding run");
        if (run.id() != null || run.status() != FeedingRunStatus.PROCESSING) {
            throw new IllegalArgumentException("only a new processing run can be inserted");
        }
        CompanionFeedRunEntity row = toRow(run);
        feedRunMapper.insert(row);
        return loadById(row.getId()).orElseThrow(() -> new IllegalStateException("喂养运行写入后无法读取"));
    }

    @Override
    public boolean restart(long runId, long expectedRevision, Instant now) {
        Optional<FeedingRun> current = loadById(runId);
        if (current.isEmpty() || (current.get().status() != FeedingRunStatus.FAILED
                && current.get().status() != FeedingRunStatus.PROCESSING)) {
            return false;
        }
        return transition(runId, expectedRevision, current.get().status(),
                current.get().restarted(notBeforePersistedUpdate(current.get(), now)));
    }

    @Override
    public boolean complete(long runId, long expectedRevision, long growthRecordId, Instant now) {
        return transitionFromProcessing(runId, expectedRevision,
                run -> run.completed(growthRecordId, notBeforePersistedUpdate(run, now)));
    }

    @Override
    public boolean fail(long runId, long expectedRevision, String safeCode, String safeMessage, Instant now) {
        return transitionFromProcessing(runId, expectedRevision,
                run -> run.failed(safeCode, safeMessage, notBeforePersistedUpdate(run, now)));
    }

    @Override
    public boolean reject(long runId, long expectedRevision, String safeCode, String safeMessage, Instant now) {
        return transitionFromProcessing(runId, expectedRevision,
                run -> run.rejected(safeCode, safeMessage, notBeforePersistedUpdate(run, now)));
    }

    private boolean transitionFromProcessing(long runId, long expectedRevision,
                                             java.util.function.Function<FeedingRun, FeedingRun> targetFactory) {
        Optional<FeedingRun> current = loadById(runId);
        if (current.isEmpty() || current.get().status() != FeedingRunStatus.PROCESSING) {
            return false;
        }
        return transition(runId, expectedRevision, FeedingRunStatus.PROCESSING, targetFactory.apply(current.get()));
    }

    private static Instant notBeforePersistedUpdate(FeedingRun current, Instant requested) {
        Objects.requireNonNull(requested, "transition time");
        // MySQL TIMESTAMP(0) may round a written fractional instant into the next second. The repository
        // must not feed that storage artifact back as a false domain-level clock reversal.
        return requested.isBefore(current.updatedAt()) ? current.updatedAt() : requested;
    }

    /** 条件更新状态与版本；更新 0 行说明记录已经不处于调用方观察到的旧状态。 */
    private boolean transition(long id, long expectedRevision, FeedingRunStatus source, FeedingRun target) {
        UpdateWrapper<CompanionFeedRunEntity> update = new UpdateWrapper<>();
        update.eq("id", id)
                .eq("revision", expectedRevision)
                .eq("status", source.name())
                .set("status", target.status().name())
                .set("resultGrowthRecordId", target.resultGrowthRecordId())
                .set("safeErrorCode", target.safeErrorCode())
                .set("safeErrorMessage", target.safeErrorMessage())
                .set("safeErrorTime", target.safeErrorTime() == null
                        ? null : Date.from(target.safeErrorTime()))
                .set("attemptCount", target.attemptCount())
                .set("revision", target.revision())
                .set("updateTime", Date.from(target.updatedAt()));
        return feedRunMapper.update(null, update) == 1;
    }

    private Optional<FeedingRun> loadById(long runId) {
        return Optional.ofNullable(feedRunMapper.selectById(runId)).map(this::fromRow);
    }

    private FeedingRun fromRow(CompanionFeedRunEntity row) {
        return new FeedingRun(row.getId(), row.getCompanionId(), row.getSubjectId(), row.getPictureId(),
                row.getIdempotencyKey(), row.getRequestFingerprint(), row.getCorrelationId(),
                FeedingRunStatus.valueOf(row.getStatus()), NutritionPolicy.valueOf(row.getRequestedPolicy()),
                row.getRequestedProviderCode(), row.getRequestedModelCode(), row.getResultGrowthRecordId(),
                row.getSafeErrorCode(), row.getSafeErrorMessage(),
                row.getSafeErrorTime() == null ? null : row.getSafeErrorTime().toInstant(),
                row.getAttemptCount(), row.getRevision(),
                Objects.requireNonNull(row.getCreateTime(), "feed run createTime").toInstant(),
                Objects.requireNonNull(row.getUpdateTime(), "feed run updateTime").toInstant());
    }

    private CompanionFeedRunEntity toRow(FeedingRun run) {
        CompanionFeedRunEntity row = new CompanionFeedRunEntity();
        row.setId(run.id());
        row.setCompanionId(run.companionId());
        row.setSubjectId(run.subjectId());
        row.setPictureId(run.pictureId());
        row.setIdempotencyKey(run.idempotencyKey());
        row.setRequestFingerprint(run.requestFingerprint());
        row.setCorrelationId(run.correlationId());
        row.setStatus(run.status().name());
        row.setRequestedPolicy(run.requestedPolicy().name());
        row.setRequestedProviderCode(run.requestedProviderCode());
        row.setRequestedModelCode(run.requestedModelCode());
        row.setResultGrowthRecordId(run.resultGrowthRecordId());
        row.setSafeErrorCode(run.safeErrorCode());
        row.setSafeErrorMessage(run.safeErrorMessage());
        row.setSafeErrorTime(run.safeErrorTime() == null ? null : Date.from(run.safeErrorTime()));
        row.setAttemptCount(run.attemptCount());
        row.setRevision(run.revision());
        row.setCreateTime(Date.from(run.createdAt()));
        row.setUpdateTime(Date.from(run.updatedAt()));
        return row;
    }
}
