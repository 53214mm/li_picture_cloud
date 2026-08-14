package com.li.lipicturecloud.domain.airuntime;

import java.util.List;
import java.util.Optional;

/**
 * 创作任务的持久化端口。(subjectId, idempotencyKey) 唯一；写入走 revision CAS。
 */
public interface CreationTaskRepository {

    Optional<CreationTask> findBySubjectAndKey(long subjectId, String idempotencyKey);

    Optional<CreationTask> findById(long id);

    List<CreationTask> findBySubjectId(long subjectId, int limit);

    CreationTask insert(CreationTask task);

    boolean save(CreationTask after, long expectedRevision);
}
