package com.li.lipicturecloud.domain.companion;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 集中保存成长数值规则的不可变对象。
 *
 * <p>把经验、等级和性格上限放在领域层，而不是散落在控制器或 SQL 中，可以让一次喂养的
 * 结果可复算，也为以后发布新的平衡版本留下位置。</p>
 */
public final class CompanionBalance {

    // V1 是已落库伙伴所使用的规则版本；修改数值时应新建版本，而不是静默改写历史规则。
    private static final CompanionBalance V1 = new CompanionBalance(
            "life-core-v1", ZoneId.of("Asia/Shanghai"),
            60L, 300L, 1L, 3L, 25L,
            new BigDecimal("1.00"), new BigDecimal("80.00"));
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal HARD_MINIMUM = new BigDecimal("-100.00");
    private static final BigDecimal HARD_MAXIMUM = new BigDecimal("100.00");

    private final String version;
    private final ZoneId dayZone;
    private final long fullFeedExperienceCap;
    private final long dailyExperienceCap;
    private final long revisitExperienceCap;
    private final long revisitExperienceLifetimeCap;
    private final long skillExperienceCap;
    private final BigDecimal traitMovementCap;
    private final BigDecimal traitSoftLimit;

    private CompanionBalance(String version, ZoneId dayZone, long fullFeedExperienceCap,
                             long dailyExperienceCap, long revisitExperienceCap,
                             long revisitExperienceLifetimeCap, long skillExperienceCap,
                             BigDecimal traitMovementCap, BigDecimal traitSoftLimit) {
        this.version = Objects.requireNonNull(version, "version");
        this.dayZone = Objects.requireNonNull(dayZone, "dayZone");
        this.fullFeedExperienceCap = fullFeedExperienceCap;
        this.dailyExperienceCap = dailyExperienceCap;
        this.revisitExperienceCap = revisitExperienceCap;
        this.revisitExperienceLifetimeCap = revisitExperienceLifetimeCap;
        this.skillExperienceCap = skillExperienceCap;
        this.traitMovementCap = normalize(traitMovementCap);
        this.traitSoftLimit = normalize(traitSoftLimit);
    }

    public static CompanionBalance v1() {
        return V1;
    }

    public String version() {
        return version;
    }

    public long totalExperienceForLevel(int level) {
        if (level < 1) {
            throw new IllegalArgumentException("level must be positive");
        }
        BigInteger threshold = experienceThreshold(level);
        return threshold.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0
                ? Long.MAX_VALUE : threshold.longValueExact();
    }

    public int levelFor(long experience) {
        if (experience < 0) {
            throw new IllegalArgumentException("experience must be nonnegative");
        }
        BigInteger target = BigInteger.valueOf(experience);
        // 等级阈值单调递增，二分查找可避免高等级时逐级循环，也不会产生浮点误差。
        int low = 1;
        int high = Integer.MAX_VALUE;
        while (low < high) {
            int middle = low + (int) ((((long) high - low) + 1L) / 2L);
            if (experienceThreshold(middle).compareTo(target) <= 0) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    public long nextLevelExperience(long experience) {
        return totalExperienceForLevel(Math.addExact(levelFor(experience), 1));
    }

    public CompanionStage stageFor(int level) {
        if (level <= 2) {
            return CompanionStage.LIGHT;
        }
        if (level <= 6) {
            return CompanionStage.SEEDLING;
        }
        return CompanionStage.COMPANION;
    }

    public int skillLevelFor(long experience) {
        return levelFor(experience);
    }

    public long nextSkillLevelExperience(long experience) {
        return nextLevelExperience(experience);
    }

    public Instant startOfDay(Instant now) {
        return Objects.requireNonNull(now, "now").atZone(dayZone)
                .toLocalDate().atStartOfDay(dayZone).toInstant();
    }

    public long fullFeedExperience(long requested, long earnedToday) {
        return Math.min(Math.min(Math.max(requested, 0L), fullFeedExperienceCap), remainingDaily(earnedToday));
    }

    public long revisitExperience(long earnedToday, long earnedForPicture) {
        if (earnedToday < 0 || earnedForPicture < 0) {
            throw new IllegalArgumentException("feeding totals must be nonnegative");
        }
        return Math.min(Math.min(revisitExperienceCap,
                Math.max(revisitExperienceLifetimeCap - earnedForPicture, 0L)), remainingDaily(earnedToday));
    }

    public long skillExperience(long requested) {
        return Math.min(Math.max(requested, 0L), skillExperienceCap);
    }

    /**
     * 将模型/规则“请求”的性格变化变成最终允许写入的变化量。
     *
     * <p>返回的是实际 delta，而不是最终值：这样伙伴聚合与成长记录可以写入同一个值。
     * 进入软边界（±80）后只允许向中间回归；硬边界（±100）则防御脏历史数据，避免一次
     * 喂养把数值突然拉回正常区间。</p>
     */
    public BigDecimal applyTrait(BigDecimal current, BigDecimal requested) {
        BigDecimal normalizedCurrent = normalize(current);
        if (normalizedCurrent.compareTo(HARD_MINIMUM) < 0 || normalizedCurrent.compareTo(HARD_MAXIMUM) > 0) {
            throw new IllegalArgumentException("current trait must be between -100.00 and 100.00");
        }
        BigDecimal boundedRequest = normalize(requested).max(traitMovementCap.negate()).min(traitMovementCap);
        if (normalizedCurrent.compareTo(traitSoftLimit) > 0) {
            return boundedRequest.min(ZERO);
        }
        if (normalizedCurrent.compareTo(traitSoftLimit.negate()) < 0) {
            return boundedRequest.max(ZERO);
        }
        BigDecimal result = normalizedCurrent.add(boundedRequest)
                .max(traitSoftLimit.negate()).min(traitSoftLimit);
        return normalize(result.subtract(normalizedCurrent));
    }

    private long remainingDaily(long earnedToday) {
        if (earnedToday < 0) {
            throw new IllegalArgumentException("earnedToday must be nonnegative");
        }
        return Math.max(dailyExperienceCap - earnedToday, 0L);
    }

    private static BigDecimal normalize(BigDecimal value) {
        return Objects.requireNonNull(value, "trait value").setScale(2, RoundingMode.HALF_UP);
    }

    private static BigInteger experienceThreshold(int level) {
        return BigInteger.valueOf(50L)
                .multiply(BigInteger.valueOf(level))
                .multiply(BigInteger.valueOf(level - 1L));
    }
}
