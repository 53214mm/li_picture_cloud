package com.li.lipicturecloud.domain.airuntime;

import java.time.Instant;
import java.util.Objects;

/**
 * 一次连接测试成功后的能力画像快照（追加式）。未知能力一律按不支持处理。
 */
public record ModelCapabilityProfile(
        Long id,
        long connectionId,
        long subjectId,
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

    public ModelCapabilityProfile {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (connectionId <= 0 || subjectId <= 0) {
            throw new IllegalArgumentException("invalid capability profile identity");
        }
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(modelCode, "modelCode");
        if (maxContextTokens != null && maxContextTokens <= 0) {
            throw new IllegalArgumentException("maxContextTokens must be positive or null");
        }
        if (!ModelCapabilities.SYNC.equals(syncAsync) && !ModelCapabilities.ASYNC.equals(syncAsync)
                && !ModelCapabilities.UNKNOWN.equals(syncAsync)) {
            throw new IllegalArgumentException("unsupported sync/async mode: " + syncAsync);
        }
        if (costHint != null && !ModelCapabilities.COST_CHEAP.equals(costHint)
                && !ModelCapabilities.COST_STANDARD.equals(costHint)
                && !ModelCapabilities.COST_EXPENSIVE.equals(costHint)) {
            throw new IllegalArgumentException("unsupported cost hint: " + costHint);
        }
        Objects.requireNonNull(createdTime, "createdTime");
    }

    public static ModelCapabilityProfile snapshot(long connectionId, long subjectId,
                                                  ModelProvider provider, String modelCode,
                                                  ModelCapabilities capabilities, Instant now) {
        return new ModelCapabilityProfile(null, connectionId, subjectId, provider, modelCode,
                capabilities.text(), capabilities.vision(), capabilities.toolCall(),
                capabilities.structuredOutput(), capabilities.reasoning(),
                capabilities.embedding(), capabilities.imageGeneration(),
                capabilities.maxContextTokens(), capabilities.syncAsync(),
                capabilities.costHint(), now);
    }

    public ModelCapabilityProfile withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new ModelCapabilityProfile(persistedId, connectionId, subjectId, provider,
                modelCode, text, vision, toolCall, structuredOutput, reasoning, embedding,
                imageGeneration, maxContextTokens, syncAsync, costHint, createdTime);
    }
}
