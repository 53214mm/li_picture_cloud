package com.li.lipicturecloud.domain.companion;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 一次已经发生、不可改写的伙伴成长事实。
 *
 * <p>它和 {@link FeedingRun} 的区别可以类比为：</p>
 * <ul>
 *     <li>FeedingRun 是“任务进度单”，记录请求正在处理、成功还是失败；</li>
 *     <li>GrowthRecord 是“成长收据”，只有成长真正发生后才生成。</li>
 * </ul>
 *
 * <p>记录中同时保存三类信息：</p>
 * <ul>
 *     <li>原因：图片、事件类型、分析来源 provenance、规则版本；</li>
 *     <li>变化：生命经验、特质和技能经验的本次增量；</li>
 *     <li>结果：成长后的完整伙伴快照 companionAfter。</li>
 * </ul>
 *
 * <p>feedingRunId 把收据关联回任务，idempotencyKey 防止同一用户意图重复成长，
 * correlationId 用于跨日志追踪。Repository 对该对象只提供追加和查询，不提供修改历史的能力。</p>
 */
public record GrowthRecord(
        Long id,
        long feedingRunId,
        long companionId,
        long pictureId,
        GrowthEventType eventType,
        long lifeExperienceDelta,
        TraitDelta traitDelta,
        Map<CompanionSkill, Long> skillExperienceDelta,
        Companion companionAfter,
        String reason,
        NutritionProvenance provenance,
        String balanceVersion,
        String idempotencyKey,
        String correlationId,
        Instant createdTime) {

    /**
     * record 的紧凑构造器，集中保护成长事实的完整性。
     * Map.copyOf 会制作不可变副本，避免调用方在记录创建后偷偷修改技能增量。
     */
    public GrowthRecord {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (feedingRunId <= 0 || companionId <= 0 || pictureId <= 0 || lifeExperienceDelta < 0) {
            throw new IllegalArgumentException("invalid growth record identity or delta");
        }
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(traitDelta, "traitDelta");
        Objects.requireNonNull(skillExperienceDelta, "skillExperienceDelta");
        if (skillExperienceDelta.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0)) {
            throw new IllegalArgumentException("skill experience delta must be nonnegative");
        }
        skillExperienceDelta = Map.copyOf(skillExperienceDelta);
        Objects.requireNonNull(companionAfter, "companionAfter");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(balanceVersion, "balanceVersion");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(createdTime, "createdTime");
    }

    /** Compatibility constructor for pre-vision persisted and test fixtures. */
    public GrowthRecord(Long id, long feedingRunId, long companionId, long pictureId,
                        GrowthEventType eventType, long lifeExperienceDelta, TraitDelta traitDelta,
                        Map<CompanionSkill, Long> skillExperienceDelta, Companion companionAfter,
                        String reason, NutritionMode nutritionMode, boolean contentUnderstood,
                        String balanceVersion, String idempotencyKey, String correlationId,
                        Instant createdTime) {
        this(id, feedingRunId, companionId, pictureId, eventType, lifeExperienceDelta, traitDelta,
                skillExperienceDelta, companionAfter, reason,
                legacyProvenance(nutritionMode, contentUnderstood), balanceVersion,
                idempotencyKey, correlationId, createdTime);
    }

    /**
     * 把领域计算得到的 FeedingGrowth 与营养分析来源组合成一张尚未入库的成长收据。
     * 此时 id 为 null，持久化成功后由 {@link #withId(long)} 返回带主键的新对象。
     */
    public static GrowthRecord from(long feedingRunId, long companionId, long pictureId,
                                    FeedingGrowth growth, NutritionProvenance provenance,
                                    String idempotencyKey,
                                    String correlationId, Instant createdTime) {
        Objects.requireNonNull(growth, "growth");
        return new GrowthRecord(null, feedingRunId, companionId, pictureId,
                growth.eventType(), growth.lifeExperienceDelta(), growth.traitDelta(),
                growth.skillExperienceDelta(), growth.companionAfter(), growth.reason(),
                provenance, growth.balanceVersion(), idempotencyKey,
                correlationId, createdTime);
    }

    /** Compatibility factory for the deterministic analyzers before a real visual Provider exists. */
    public static GrowthRecord from(long feedingRunId, long companionId, long pictureId,
                                    FeedingGrowth growth, NutritionMode nutritionMode,
                                    boolean contentUnderstood, String idempotencyKey,
                                    String correlationId, Instant createdTime) {
        return from(feedingRunId, companionId, pictureId, growth,
                legacyProvenance(nutritionMode, contentUnderstood), idempotencyKey,
                correlationId, createdTime);
    }

    /**
     * 数据库写入成功后补上主键。由于 record 不可变，该方法返回新对象而不修改原记录。
     */
    public GrowthRecord withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new GrowthRecord(persistedId, feedingRunId, companionId, pictureId, eventType,
                lifeExperienceDelta, traitDelta, skillExperienceDelta, companionAfter, reason,
                provenance, balanceVersion, idempotencyKey,
                correlationId, createdTime);
    }

    /** @deprecated Use {@link #provenance()} as the immutable audit fact. */
    @Deprecated(forRemoval = false)
    public NutritionMode nutritionMode() {
        return provenance.actualMode();
    }

    /** @deprecated Use {@link #provenance()} as the immutable audit fact. */
    @Deprecated(forRemoval = false)
    public boolean contentUnderstood() {
        return provenance.contentUnderstood();
    }

    /**
     * 把早期只有 NutritionMode 的旧数据转换成新的审计来源对象，仅用于兼容历史记录。
     */
    private static NutritionProvenance legacyProvenance(NutritionMode mode, boolean contentUnderstood) {
        if (contentUnderstood) {
            throw new IllegalArgumentException("legacy growth record cannot claim content understanding");
        }
        return switch (Objects.requireNonNull(mode, "nutritionMode")) {
            case DEMO_DETERMINISTIC -> NutritionProvenance.demo();
            case METADATA_DETERMINISTIC -> NutritionProvenance.metadata();
            case VISUAL_MODEL -> throw new IllegalArgumentException("visual growth record requires explicit provenance");
        };
    }
}
