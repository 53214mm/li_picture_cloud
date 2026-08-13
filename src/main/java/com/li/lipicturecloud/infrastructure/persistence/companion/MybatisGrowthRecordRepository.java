package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.domain.companion.GrowthEventType;
import com.li.lipicturecloud.domain.companion.GrowthRecord;
import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import com.li.lipicturecloud.domain.companion.NutritionMode;
import com.li.lipicturecloud.domain.companion.NutritionProvenance;
import com.li.lipicturecloud.mapper.CompanionGrowthRecordMapper;
import com.li.lipicturecloud.model.entity.CompanionGrowthRecordEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisGrowthRecordRepository implements GrowthRecordRepository {

    private final CompanionGrowthRecordMapper growthRecordMapper;
    private final CompanionJsonCodec jsonCodec;

    public MybatisGrowthRecordRepository(CompanionGrowthRecordMapper growthRecordMapper,
                                         CompanionJsonCodec jsonCodec) {
        this.growthRecordMapper = growthRecordMapper;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public GrowthRecord append(GrowthRecord record) {
        Objects.requireNonNull(record, "growth record");
        CompanionGrowthRecordEntity row = toRow(record);
        if (row.getId() == null) {
            row.setId(IdWorker.getId());
        }
        growthRecordMapper.insert(row);
        CompanionGrowthRecordEntity stored = growthRecordMapper.selectById(row.getId());
        if (stored == null) {
            throw new IllegalStateException("伙伴成长记录写入后无法读取");
        }
        return fromRow(stored);
    }

    @Override
    public Optional<GrowthRecord> findByFeedingRunId(long feedingRunId) {
        return Optional.ofNullable(growthRecordMapper.selectByFeedingRunId(feedingRunId))
                .map(this::fromRow);
    }

    @Override
    public List<GrowthRecord> findRecent(long companionId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return growthRecordMapper.selectRecent(companionId, safeLimit)
                .stream().map(this::fromRow).toList();
    }

    @Override
    public boolean hasFullFeed(long companionId, long pictureId) {
        return growthRecordMapper.countFullFeeds(companionId, pictureId) > 0;
    }

    @Override
    public long sumLifeExperienceSince(long companionId, Instant since) {
        return growthRecordMapper.sumLifeExperienceSince(companionId, Date.from(Objects.requireNonNull(since, "since")));
    }

    @Override
    public long sumRevisitExperience(long companionId, long pictureId) {
        return growthRecordMapper.sumRevisitExperience(companionId, pictureId);
    }

    private GrowthRecord fromRow(CompanionGrowthRecordEntity row) {
        return new GrowthRecord(row.getId(), row.getFeedingRunId(), row.getCompanionId(), row.getPictureId(),
                GrowthEventType.valueOf(row.getEventType()), row.getLifeExperienceDelta(),
                jsonCodec.readTraitDelta(row.getTraitDeltaJson()), jsonCodec.readSkillDelta(row.getSkillDeltaJson()),
                jsonCodec.readSnapshot(row.getSnapshotJson(), CompanionBalance.v1()), row.getReason(),
                new NutritionProvenance(NutritionMode.valueOf(row.getNutritionMode()),
                        Boolean.TRUE.equals(row.getContentUnderstood()), row.getProviderCode(),
                        row.getModelCode(), row.getPromptVersion(), row.getResultSchemaVersion(),
                        row.getConfidence(), row.getFallbackReasonCode()),
                row.getBalanceVersion(), row.getIdempotencyKey(), row.getCorrelationId(),
                Objects.requireNonNull(row.getCreateTime(), "growth createTime").toInstant());
    }

    private CompanionGrowthRecordEntity toRow(GrowthRecord record) {
        CompanionGrowthRecordEntity row = new CompanionGrowthRecordEntity();
        row.setId(record.id());
        row.setFeedingRunId(record.feedingRunId());
        row.setCompanionId(record.companionId());
        row.setPictureId(record.pictureId());
        row.setEventType(record.eventType().name());
        row.setLifeExperienceDelta(record.lifeExperienceDelta());
        row.setTraitDeltaJson(jsonCodec.writeTraitDelta(record.traitDelta()));
        row.setSkillDeltaJson(jsonCodec.writeSkillDelta(record.skillExperienceDelta()));
        row.setSnapshotJson(jsonCodec.writeSnapshot(record.companionAfter()));
        row.setReason(record.reason());
        row.setNutritionMode(record.provenance().actualMode().name());
        row.setContentUnderstood(record.provenance().contentUnderstood());
        row.setProviderCode(record.provenance().providerCode());
        row.setModelCode(record.provenance().modelCode());
        row.setPromptVersion(record.provenance().promptVersion());
        row.setResultSchemaVersion(record.provenance().resultSchemaVersion());
        row.setConfidence(record.provenance().confidence());
        row.setFallbackReasonCode(record.provenance().fallbackReasonCode());
        row.setBalanceVersion(record.balanceVersion());
        row.setIdempotencyKey(record.idempotencyKey());
        row.setCorrelationId(record.correlationId());
        row.setCreateTime(Date.from(record.createdTime()));
        return row;
    }
}
