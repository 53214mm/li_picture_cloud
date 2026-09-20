package com.li.lipicturecloud.domain.airuntime;

import java.util.Optional;

/**
 * 融合生成图片暂存的持久化端口。每任务一份（taskId 唯一）。
 */
public interface CreationFusionImageRepository {

    CreationFusionImage insert(CreationFusionImage image);

    Optional<CreationFusionImage> findByTaskId(long taskId);
}
