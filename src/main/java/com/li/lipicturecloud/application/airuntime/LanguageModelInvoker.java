package com.li.lipicturecloud.application.airuntime;

import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 语言模型流式调用端口。实现只消费 ModelRouteDecision 中的路由信息，
 * 失败以 {@link ModelInvocationException}（仅安全错误码）终止流。
 */
public interface LanguageModelInvoker {

    Flux<String> stream(ModelRouteDecision route, List<ChatTurn> turns);
}
