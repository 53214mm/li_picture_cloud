package com.li.lipicturecloud.domain.recipe;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 一个用户玩法配方。配方只组合白名单能力且只能收紧权限与费用，不能扩大。
 * 状态机 DRAFT/ENABLED/DISABLED 显式转移，每次转移 revision 恰好 +1。
 */
public record Recipe(
        Long id,
        long subjectId,
        String name,
        RecipeStatus status,
        long revision,
        Instant createdTime,
        Instant updatedTime) {

    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N} _\\-]{1,64}");

    public Recipe {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (subjectId <= 0 || revision < 0) {
            throw new IllegalArgumentException("invalid recipe identity or revision");
        }
        if (name == null || !NAME.matcher(name.strip()).matches()) {
            throw new IllegalArgumentException("recipe name must be 1-64 safe characters");
        }
        name = name.strip();
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdTime, "createdTime");
        Objects.requireNonNull(updatedTime, "updatedTime");
        if (updatedTime.isBefore(createdTime)) {
            throw new IllegalArgumentException("updatedTime cannot be before createdTime");
        }
    }

    public static Recipe create(long subjectId, String name, Instant now) {
        return new Recipe(null, subjectId, name, RecipeStatus.DRAFT, 0L, now, now);
    }

    public static Recipe restore(Long id, long subjectId, String name, RecipeStatus status,
                                 long revision, Instant createdTime, Instant updatedTime) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("persisted id must be positive");
        }
        return new Recipe(id, subjectId, name, status, revision, createdTime, updatedTime);
    }

    public Recipe withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new Recipe(persistedId, subjectId, name, status, revision, createdTime, updatedTime);
    }

    public Recipe enable(Instant now) {
        if (status != RecipeStatus.DRAFT && status != RecipeStatus.DISABLED) {
            throw new IllegalStateException("enable requires DRAFT or DISABLED but recipe is " + status);
        }
        return transition(RecipeStatus.ENABLED, now);
    }

    public Recipe disable(Instant now) {
        requireStatus(RecipeStatus.ENABLED, "disable");
        return transition(RecipeStatus.DISABLED, now);
    }

    private void requireStatus(RecipeStatus expected, String operation) {
        if (status != expected) {
            throw new IllegalStateException(operation + " requires " + expected
                    + " but recipe is " + status);
        }
    }

    private Recipe transition(RecipeStatus next, Instant now) {
        return new Recipe(id, subjectId, name, next, Math.addExact(revision, 1L), createdTime,
                Objects.requireNonNull(now, "now"));
    }
}
