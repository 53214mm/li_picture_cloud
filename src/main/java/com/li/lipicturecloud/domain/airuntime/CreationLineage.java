package com.li.lipicturecloud.domain.airuntime;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 创作血缘（追加式）：记录每个来源图片在创作中使用的语言能力与模型。
 * 来源图片删除/撤权后血缘保留引用；文本作品 resultPictureId 为空，
 * 融合图等回库作品保存后写入结果图片 ID。
 */
public record CreationLineage(
        Long id,
        long taskId,
        long sourcePictureId,
        Long resultPictureId,
        String capabilityId,
        String modelCode,
        String promptTemplateVersion,
        String costSource,
        Instant createdTime) {

    private static final Pattern CODE = Pattern.compile("[a-zA-Z0-9._\\-]{1,64}");

    public CreationLineage {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (taskId <= 0 || sourcePictureId <= 0) {
            throw new IllegalArgumentException("invalid lineage identity");
        }
        if (resultPictureId != null && resultPictureId <= 0) {
            throw new IllegalArgumentException("resultPictureId must be positive or null");
        }
        if (capabilityId == null || !CODE.matcher(capabilityId).matches()) {
            throw new IllegalArgumentException("capabilityId must match " + CODE.pattern());
        }
        if (modelCode == null || !CODE.matcher(modelCode).matches()) {
            throw new IllegalArgumentException("modelCode must match " + CODE.pattern());
        }
        if (promptTemplateVersion == null || !CODE.matcher(promptTemplateVersion).matches()) {
            throw new IllegalArgumentException("promptTemplateVersion must match " + CODE.pattern());
        }
        if (costSource == null || !CODE.matcher(costSource).matches()) {
            throw new IllegalArgumentException("costSource must match " + CODE.pattern());
        }
        Objects.requireNonNull(createdTime, "createdTime");
    }

    public CreationLineage withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new CreationLineage(persistedId, taskId, sourcePictureId, resultPictureId,
                capabilityId, modelCode, promptTemplateVersion, costSource, createdTime);
    }
}
