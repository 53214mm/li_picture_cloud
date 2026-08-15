package com.li.lipicturecloud.domain.recipe;

import java.time.Instant;
import java.util.Objects;

/**
 * 配方版本（append-only）：每个版本固化一份定义 JSON。
 * JSON 只允许安全纯文本与白名单键值（语义由 {@link RecipeDefinition} 与编解码器保证），
 * 本记录只做结构性约束：长度上限、无控制字符。
 */
public record RecipeVersion(
        Long id,
        long recipeId,
        int version,
        String whenJson,
        String ifJson,
        String thenJson,
        Instant createdTime) {

    public static final int MAX_JSON_CODE_POINTS = 4000;

    public RecipeVersion {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (recipeId <= 0 || version < 1) {
            throw new IllegalArgumentException("invalid recipe version identity");
        }
        whenJson = checkJson(whenJson, "whenJson");
        ifJson = checkJson(ifJson, "ifJson");
        thenJson = checkJson(thenJson, "thenJson");
        Objects.requireNonNull(createdTime, "createdTime");
    }

    public static RecipeVersion create(long recipeId, int version, String whenJson,
                                       String ifJson, String thenJson, Instant now) {
        return new RecipeVersion(null, recipeId, version, whenJson, ifJson, thenJson, now);
    }

    public static RecipeVersion restore(Long id, long recipeId, int version, String whenJson,
                                        String ifJson, String thenJson, Instant createdTime) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("persisted id must be positive");
        }
        return new RecipeVersion(id, recipeId, version, whenJson, ifJson, thenJson, createdTime);
    }

    public RecipeVersion withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new RecipeVersion(persistedId, recipeId, version, whenJson, ifJson, thenJson,
                createdTime);
    }

    private static String checkJson(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > MAX_JSON_CODE_POINTS) {
            throw new IllegalArgumentException(field + " must be 1-" + MAX_JSON_CODE_POINTS
                    + " characters");
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " must be safe plain text");
        }
        return normalized;
    }
}
