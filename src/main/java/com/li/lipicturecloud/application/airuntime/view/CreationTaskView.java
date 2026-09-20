package com.li.lipicturecloud.application.airuntime.view;

import com.li.lipicturecloud.domain.airuntime.CreationKind;
import com.li.lipicturecloud.domain.airuntime.CreationStatus;
import com.li.lipicturecloud.domain.airuntime.CreationTask;

import java.time.Instant;
import java.util.List;

/**
 * 创作任务的展示视图：生成文本是伙伴内容，可公开回显（保存作品用）。
 */
public record CreationTaskView(
        long id,
        CreationKind kind,
        CreationStatus status,
        List<Long> sourcePictureIds,
        String outlineText,
        String draftText,
        String resultText,
        Long modelConnectionId,
        String idempotencyKey,
        long revision,
        Instant createdTime,
        Instant updatedTime) {

    public static CreationTaskView of(CreationTask task) {
        return new CreationTaskView(task.id(), task.kind(), task.status(),
                task.sourcePictureIds(), task.outlineText(), task.draftText(), task.resultText(),
                task.modelConnectionId(), task.idempotencyKey(), task.revision(),
                task.createdTime(), task.updatedTime());
    }
}
