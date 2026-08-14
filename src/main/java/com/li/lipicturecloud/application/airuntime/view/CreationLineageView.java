package com.li.lipicturecloud.application.airuntime.view;

import com.li.lipicturecloud.domain.airuntime.CreationLineage;

import java.time.Instant;

/**
 * 创作血缘的展示视图。
 */
public record CreationLineageView(
        long id,
        long taskId,
        long sourcePictureId,
        String capabilityId,
        String modelCode,
        String promptTemplateVersion,
        String costSource,
        Instant createdTime) {

    public static CreationLineageView of(CreationLineage lineage) {
        return new CreationLineageView(lineage.id(), lineage.taskId(), lineage.sourcePictureId(),
                lineage.capabilityId(), lineage.modelCode(), lineage.promptTemplateVersion(),
                lineage.costSource(), lineage.createdTime());
    }
}
