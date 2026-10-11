package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import java.util.Set;

/** Documented, exact-model policy for bounded background work. Normal chat is unchanged. */
public record BoundedGenerationProfile(String thinkingMode, String tokenLimitParameter,
                                       int collectionTimeoutSeconds) {
    public static BoundedGenerationProfile forConnection(ModelConnection connection) {
        if (connection != null && "DASHSCOPE".equals(connection.provider().name())
                && Set.of("qwen3.8-max", "qwen3.8-max-0902").contains(connection.modelCode())) {
            return new BoundedGenerationProfile("NON_THINKING", "max_completion_tokens", 15);
        }
        return new BoundedGenerationProfile("PROVIDER_DEFAULT", "max_tokens", 8);
    }
    public boolean nonThinking() { return "NON_THINKING".equals(thinkingMode); }
}
