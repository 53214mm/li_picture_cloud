package com.li.lipicturecloud.domain.companion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 伙伴与一个主体之间的关系状态：慢变量，不随时间衰减。
 *
 * <p>熟悉度、信任、亲密度、默契为正向积累，近期反馈保留方向（正负），
 * 为后续敲打/安静反馈预留语义。</p>
 */
public record CompanionRelationship(
        Long id,
        long companionId,
        long subjectId,
        BigDecimal familiarity,
        BigDecimal trust,
        BigDecimal closeness,
        BigDecimal tacit,
        BigDecimal recentFeedback,
        long revision) {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal MAXIMUM = new BigDecimal("100.00");
    private static final BigDecimal MINIMUM = new BigDecimal("-100.00");

    public CompanionRelationship {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (companionId <= 0 || subjectId <= 0 || revision < 0) {
            throw new IllegalArgumentException("invalid relationship identity or revision");
        }
        familiarity = normalizePositive(familiarity);
        trust = normalizePositive(trust);
        closeness = normalizePositive(closeness);
        tacit = normalizePositive(tacit);
        recentFeedback = normalizeSigned(recentFeedback);
    }

    public static CompanionRelationship initial(long companionId, long subjectId) {
        if (companionId <= 0 || subjectId <= 0) {
            throw new IllegalArgumentException("companionId and subjectId must be positive");
        }
        return new CompanionRelationship(null, companionId, subjectId, ZERO, ZERO, ZERO, ZERO, ZERO, 0L);
    }

    public static CompanionRelationship restore(Long id, long companionId, long subjectId,
                                                BigDecimal familiarity, BigDecimal trust,
                                                BigDecimal closeness, BigDecimal tacit,
                                                BigDecimal recentFeedback, long revision) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("persisted id must be positive");
        }
        return new CompanionRelationship(id, companionId, subjectId, familiarity, trust,
                closeness, tacit, recentFeedback, revision);
    }

    public CompanionRelationship withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new CompanionRelationship(persistedId, companionId, subjectId, familiarity, trust,
                closeness, tacit, recentFeedback, revision);
    }

    public CompanionRelationship apply(RelationshipImpact impact, CompanionRelationshipRules rules) {
        Objects.requireNonNull(impact, "impact");
        Objects.requireNonNull(rules, "rules");
        return new CompanionRelationship(id, companionId, subjectId,
                applyPositive(familiarity, impact.familiarity(), rules),
                applyPositive(trust, impact.trust(), rules),
                applyPositive(closeness, impact.closeness(), rules),
                applyPositive(tacit, impact.tacit(), rules),
                applySigned(recentFeedback, impact.recentFeedback(), rules),
                Math.addExact(revision, 1L));
    }

    private static BigDecimal applyPositive(BigDecimal current, BigDecimal requested,
                                            CompanionRelationshipRules rules) {
        BigDecimal bounded = Objects.requireNonNull(requested, "impact value")
                .max(rules.maxImpact().negate()).min(rules.maxImpact());
        return current.add(bounded).max(ZERO).min(MAXIMUM);
    }

    private static BigDecimal applySigned(BigDecimal current, BigDecimal requested,
                                          CompanionRelationshipRules rules) {
        BigDecimal bounded = Objects.requireNonNull(requested, "impact value")
                .max(rules.maxImpact().negate()).min(rules.maxImpact());
        return current.add(bounded).max(MINIMUM).min(MAXIMUM);
    }

    private static BigDecimal normalizePositive(BigDecimal value) {
        BigDecimal normalized = Objects.requireNonNull(value, "relationship value")
                .setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(ZERO) < 0 || normalized.compareTo(MAXIMUM) > 0) {
            throw new IllegalArgumentException("relationship value must be between 0.00 and 100.00");
        }
        return normalized;
    }

    private static BigDecimal normalizeSigned(BigDecimal value) {
        BigDecimal normalized = Objects.requireNonNull(value, "relationship value")
                .setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(MINIMUM) < 0 || normalized.compareTo(MAXIMUM) > 0) {
            throw new IllegalArgumentException("recentFeedback must be between -100.00 and 100.00");
        }
        return normalized;
    }
}
