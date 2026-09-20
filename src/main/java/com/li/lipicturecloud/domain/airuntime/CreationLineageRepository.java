package com.li.lipicturecloud.domain.airuntime;

import java.util.List;

/**
 * 创作血缘的追加式持久化端口。
 */
public interface CreationLineageRepository {

    CreationLineage append(CreationLineage lineage);

    List<CreationLineage> findByTaskId(long taskId);
}
