package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.ModelProvider;

import java.net.URI;

/**
 * 模型端点连通性探测端口：用最小化请求验证端点可达且凭据有效。
 * 实现不得在日志、异常或结果中携带凭据或响应正文。
 */
public interface ModelConnectivityTester {

    ConnectivityResult test(URI endpointUri, String apiKey, ModelProvider provider);
}
