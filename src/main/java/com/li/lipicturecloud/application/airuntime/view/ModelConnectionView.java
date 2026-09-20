package com.li.lipicturecloud.application.airuntime.view;

import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;

/**
 * 模型连接的安全展示视图：不含任何凭据信息。
 */
public record ModelConnectionView(
        long id,
        long subjectId,
        ModelProvider provider,
        String displayName,
        String endpointUri,
        String modelCode,
        Long credentialId,
        boolean enabled,
        long revision) {

    public static ModelConnectionView of(ModelConnection connection) {
        return new ModelConnectionView(connection.id(), connection.subjectId(),
                connection.provider(), connection.displayName(),
                connection.endpointUri().toString(), connection.modelCode(),
                connection.credentialId(), connection.enabled(), connection.revision());
    }
}
