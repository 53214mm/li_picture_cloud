package com.li.lipicturecloud.domain.airuntime;

import java.util.List;
import java.util.Optional;

/**
 * 模型调用使用记录的追加式持久化端口。记录只含安全字段，无提示词、无响应正文、无 Token。
 */
public interface ModelUsageRecordRepository {

    ModelUsageRecord append(ModelUsageRecord record);

    Optional<ModelUsageRecord> findById(long id);

    /** 最近记录（按创建时间倒序），limit 由实现钳制在 [1, 100]。 */
    List<ModelUsageRecord> findRecent(long subjectId, int limit);
}
