package com.li.lipicturecloud.application.airuntime;

/**
 * 图片创作模型调用端口。实现只消费 ModelRouteDecision 中的路由信息，
 * 失败以 {@link ModelInvocationException}（仅安全错误码）抛出。
 */
public interface ImageModelInvoker {

    ImageGenerationResult invoke(ModelRouteDecision route, String prompt, String size);
}
