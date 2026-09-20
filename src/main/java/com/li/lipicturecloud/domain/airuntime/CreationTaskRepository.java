package com.li.lipicturecloud.domain.airuntime;

import java.util.List;
import java.util.Optional;

/**
 * 创作任务的持久化端口。(subjectId, idempotencyKey) 唯一；写入走 revision CAS。
 */
public interface CreationTaskRepository {

    Optional<CreationTask> findBySubjectAndKey(long subjectId, String idempotencyKey);

    Optional<CreationTask> findById(long id);

    /** 按玩法种类分页查询：kind 过滤在数据库内完成，避免 limit 先于过滤导致任务被隐藏。 */
    List<CreationTask> findBySubjectIdAndKind(long subjectId, CreationKind kind, int limit);

    CreationTask insert(CreationTask task);

    boolean save(CreationTask after, long expectedRevision);
}
