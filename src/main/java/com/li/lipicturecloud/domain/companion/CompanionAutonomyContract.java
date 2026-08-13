package com.li.lipicturecloud.domain.companion;

import java.time.LocalTime;
import java.util.Objects;

/**
 * 用户的自主契约：主动开关、安静时段与频率上限。
 *
 * <p>契约只能收紧伙伴的主动空间，不能扩大任何权限；默认关闭，
 * 用户显式开启后才可能出现主动提案。</p>
 */
public record CompanionAutonomyContract(
        Long id,
        long companionId,
        long subjectId,
        boolean active,
        LocalTime quietStart,
        LocalTime quietEnd,
        int maxFrequencyHours,
        long revision) {

    public CompanionAutonomyContract {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (companionId <= 0 || subjectId <= 0 || revision < 0) {
            throw new IllegalArgumentException("invalid contract identity or revision");
        }
        Objects.requireNonNull(quietStart, "quietStart");
        Objects.requireNonNull(quietEnd, "quietEnd");
        if (maxFrequencyHours < 0) {
            throw new IllegalArgumentException("maxFrequencyHours must be nonnegative");
        }
    }

    /** 默认契约：关闭主动、23:00-08:00 安静、每 72 小时最多一次。 */
    public static CompanionAutonomyContract initial(long companionId, long subjectId) {
        if (companionId <= 0 || subjectId <= 0) {
            throw new IllegalArgumentException("companionId and subjectId must be positive");
        }
        return new CompanionAutonomyContract(null, companionId, subjectId, false,
                LocalTime.of(23, 0), LocalTime.of(8, 0), 72, 0L);
    }

    public static CompanionAutonomyContract restore(Long id, long companionId, long subjectId,
                                                    boolean active, LocalTime quietStart, LocalTime quietEnd,
                                                    int maxFrequencyHours, long revision) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("persisted id must be positive");
        }
        return new CompanionAutonomyContract(id, companionId, subjectId, active, quietStart, quietEnd,
                maxFrequencyHours, revision);
    }

    public CompanionAutonomyContract withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new CompanionAutonomyContract(persistedId, companionId, subjectId, active, quietStart,
                quietEnd, maxFrequencyHours, revision);
    }

    public CompanionAutonomyContract updated(boolean nextActive, LocalTime nextQuietStart,
                                             LocalTime nextQuietEnd, int nextMaxFrequencyHours) {
        Objects.requireNonNull(nextQuietStart, "nextQuietStart");
        Objects.requireNonNull(nextQuietEnd, "nextQuietEnd");
        if (nextMaxFrequencyHours < 0 || nextMaxFrequencyHours > 24 * 30) {
            throw new IllegalArgumentException("maxFrequencyHours must be between 0 and 720");
        }
        return new CompanionAutonomyContract(id, companionId, subjectId, nextActive,
                nextQuietStart, nextQuietEnd, nextMaxFrequencyHours, Math.addExact(revision, 1L));
    }
}
