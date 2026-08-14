package com.li.lipicturecloud.application.airuntime.view;

import com.li.lipicturecloud.domain.airuntime.ModelCapabilityProfile;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;

import java.time.Instant;

/**
 * 能力画像快照的安全展示视图：全部字段安全，可公开回显。
 */
public record ModelCapabilityProfileView(
        long id,
        long connectionId,
        ModelProvider provider,
        String modelCode,
        boolean text,
        boolean vision,
        boolean toolCall,
        boolean structuredOutput,
        boolean reasoning,
        boolean embedding,
        boolean imageGeneration,
        Integer maxContextTokens,
        String syncAsync,
        String costHint,
        Instant createdTime) {

    public static ModelCapabilityProfileView of(ModelCapabilityProfile profile) {
        return new ModelCapabilityProfileView(profile.id(), profile.connectionId(),
                profile.provider(), profile.modelCode(), profile.text(), profile.vision(),
                profile.toolCall(), profile.structuredOutput(), profile.reasoning(),
                profile.embedding(), profile.imageGeneration(), profile.maxContextTokens(),
                profile.syncAsync(), profile.costHint(), profile.createdTime());
    }
}
