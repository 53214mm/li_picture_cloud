package com.li.lipicturecloud.domain.airuntime;

import java.util.Objects;

/**
 * 平台维护的模型能力认知快照来源：未知能力一律按"不支持"处理，
 * 绝不按供应商品牌名猜测能力。
 */
public record ModelCapabilities(
        boolean text,
        boolean vision,
        boolean toolCall,
        boolean structuredOutput,
        boolean reasoning,
        boolean embedding,
        boolean imageGeneration,
        Integer maxContextTokens,
        String syncAsync,
        String costHint) {

    public static final String SYNC = "SYNC";
    public static final String ASYNC = "ASYNC";
    public static final String UNKNOWN = "UNKNOWN";
    public static final String COST_CHEAP = "CHEAP";
    public static final String COST_STANDARD = "STANDARD";
    public static final String COST_EXPENSIVE = "EXPENSIVE";

    public ModelCapabilities {
        if (maxContextTokens != null && maxContextTokens <= 0) {
            throw new IllegalArgumentException("maxContextTokens must be positive or null");
        }
        if (!SYNC.equals(syncAsync) && !ASYNC.equals(syncAsync) && !UNKNOWN.equals(syncAsync)) {
            throw new IllegalArgumentException("unsupported sync/async mode: " + syncAsync);
        }
        if (costHint != null && !COST_CHEAP.equals(costHint) && !COST_STANDARD.equals(costHint)
                && !COST_EXPENSIVE.equals(costHint)) {
            throw new IllegalArgumentException("unsupported cost hint: " + costHint);
        }
        Objects.requireNonNull(syncAsync, "syncAsync");
    }

    public static ModelCapabilities unknown() {
        return new ModelCapabilities(false, false, false, false, false, false, false,
                null, UNKNOWN, null);
    }

    /**
     * 平台已用该模型真实完成过一次最小对话：文本能力由验证确认，其余能力仍以认知表为准。
     */
    public ModelCapabilities withVerifiedText() {
        return text ? this : new ModelCapabilities(true, vision, toolCall, structuredOutput,
                reasoning, embedding, imageGeneration, maxContextTokens, syncAsync, costHint);
    }

    public static ModelCapabilities of(boolean text, boolean vision, boolean toolCall,
                                       boolean structuredOutput, boolean reasoning,
                                       boolean embedding, boolean imageGeneration,
                                       Integer maxContextTokens, String syncAsync, String costHint) {
        return new ModelCapabilities(text, vision, toolCall, structuredOutput, reasoning,
                embedding, imageGeneration, maxContextTokens, syncAsync, costHint);
    }
}
