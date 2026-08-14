package com.li.lipicturecloud.domain.airuntime;

import java.util.Optional;

/**
 * 模型能力画像快照的追加式持久化端口。
 */
public interface ModelCapabilityProfileRepository {

    ModelCapabilityProfile append(ModelCapabilityProfile profile);

    /** 某连接的最新画像快照。 */
    Optional<ModelCapabilityProfile> findLatestByConnectionId(long connectionId);
}
