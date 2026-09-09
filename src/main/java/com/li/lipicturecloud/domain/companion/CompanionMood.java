package com.li.lipicturecloud.domain.companion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 伙伴当前情绪：会随喂养、互动和时间快速变化的五轴强度。
 *
 * <p>情绪是快状态，与长期性格分开存储；读取时惰性衰减，不进入成长审计。
 * 数值只表示强度，没有负值；孤独、烦躁同样是强度轴。</p>
 */
public record CompanionMood(
        Long id,
        long companionId,
        BigDecimal energy,
        BigDecimal joy,
        BigDecimal loneliness,
        BigDecimal inspiration,
        BigDecimal irritation,
        long revision,
        java.time.Instant updatedAt) {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal MAXIMUM = new BigDecimal("100.00");

    public CompanionMood {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (companionId <= 0 || revision < 0) {
            throw new IllegalArgumentException("invalid mood identity or revision");
        }
        energy = normalize(energy);
        joy = normalize(joy);
        loneliness = normalize(loneliness);
        inspiration = normalize(inspiration);
        irritation = normalize(irritation);
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static CompanionMood neutral(long companionId, java.time.Instant now) {
        if (companionId <= 0) {
            throw new IllegalArgumentException("companionId must be positive");
        }
        Objects.requireNonNull(now, "now");
        return new CompanionMood(null, companionId, ZERO, ZERO, ZERO, ZERO, ZERO, 0L, now);
    }

    public static CompanionMood restore(Long id, long companionId, BigDecimal energy, BigDecimal joy,
                                        BigDecimal loneliness, BigDecimal inspiration, BigDecimal irritation,
                                        long revision, java.time.Instant updatedAt) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("persisted id must be positive");
        }
        return new CompanionMood(id, companionId, energy, joy, loneliness, inspiration,
                irritation, revision, updatedAt);
    }

    public CompanionMood withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new CompanionMood(persistedId, companionId, energy, joy, loneliness, inspiration,
                irritation, revision, updatedAt);
    }

    /**
     * 每完整小时按规则向 0 收敛；任何轴都没有变化时返回自身，避免无谓写库。
     *
     * <p>衰减基准只推进到"最后一个被消费的完整小时"的结束时刻（{@code updatedAt + N 小时}），
     * 不足一小时的时间余量保留给下一次读取，例如经过 1 小时 59 分只衰减 1 小时，再过 1 分钟
     * 仍能产生第二个整小时的衰减。纯读取触发的惰性衰减不得把余量丢失为 {@code now}。</p>
     */
    public CompanionMood decayed(java.time.Instant now, CompanionMoodRules rules) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(rules, "rules");
        long elapsedHours = elapsedFullHours(now);
        if (elapsedHours <= 0) {
            return this;
        }
        java.math.BigDecimal[] decayed = decayedValues(elapsedHours, rules);
        if (decayed[0].equals(energy) && decayed[1].equals(joy) && decayed[2].equals(loneliness)
                && decayed[3].equals(inspiration) && decayed[4].equals(irritation)) {
            return this;
        }
        java.time.Instant decayAnchor = updatedAt.plus(elapsedHours, java.time.temporal.ChronoUnit.HOURS);
        return new CompanionMood(id, companionId, decayed[0], decayed[1], decayed[2],
                decayed[3], decayed[4], Math.addExact(revision, 1L), decayAnchor);
    }

    /**
     * 先按经过时间衰减，再叠加一次喂养的情绪影响；一次写入只推进一个 revision。
     *
     * <p>与惰性读取不同，喂养是真实新事件：事件时刻作为新的衰减基准（{@code updatedAt = now}），
     * 事件会打断纯时间衰减，未满整小时的时间余量不再继续累积。</p>
     */
    public CompanionMood apply(MoodImpact impact, java.time.Instant now, CompanionMoodRules rules) {
        Objects.requireNonNull(impact, "impact");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(rules, "rules");
        java.math.BigDecimal[] decayed = decayedValues(elapsedFullHours(now), rules);
        return new CompanionMood(id, companionId,
                applyAxis(decayed[0], impact.energy(), rules),
                applyAxis(decayed[1], impact.joy(), rules),
                applyAxis(decayed[2], impact.loneliness(), rules),
                applyAxis(decayed[3], impact.inspiration(), rules),
                applyAxis(decayed[4], impact.irritation(), rules),
                Math.addExact(revision, 1L), now);
    }

    public boolean sameValuesAs(CompanionMood other) {
        Objects.requireNonNull(other, "other");
        return energy.equals(other.energy) && joy.equals(other.joy) && loneliness.equals(other.loneliness)
                && inspiration.equals(other.inspiration) && irritation.equals(other.irritation);
    }

    private java.math.BigDecimal[] decayedValues(long elapsedHours, CompanionMoodRules rules) {
        if (elapsedHours <= 0) {
            return new java.math.BigDecimal[]{energy, joy, loneliness, inspiration, irritation};
        }
        java.math.BigDecimal delta = rules.decayPerHour().multiply(java.math.BigDecimal.valueOf(elapsedHours));
        return new java.math.BigDecimal[]{decay(energy, delta), decay(joy, delta), decay(loneliness, delta),
                decay(inspiration, delta), decay(irritation, delta)};
    }

    private long elapsedFullHours(java.time.Instant now) {
        return java.time.Duration.between(updatedAt, now).toHours();
    }

    private static BigDecimal decay(BigDecimal value, BigDecimal delta) {
        return value.subtract(delta).max(ZERO);
    }

    private static BigDecimal applyAxis(BigDecimal current, BigDecimal requested, CompanionMoodRules rules) {
        BigDecimal bounded = Objects.requireNonNull(requested, "impact value")
                .max(rules.maxImpact().negate()).min(rules.maxImpact());
        return current.add(bounded).max(ZERO).min(MAXIMUM);
    }

    private static BigDecimal normalize(BigDecimal value) {
        BigDecimal normalized = Objects.requireNonNull(value, "mood value")
                .setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(ZERO) < 0 || normalized.compareTo(MAXIMUM) > 0) {
            throw new IllegalArgumentException("mood must be between 0.00 and 100.00");
        }
        return normalized;
    }
}
