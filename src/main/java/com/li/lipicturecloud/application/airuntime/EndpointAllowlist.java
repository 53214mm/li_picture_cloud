package com.li.lipicturecloud.application.airuntime;

import java.net.URI;

/**
 * 模型端点白名单端口：第一阶段只允许 HTTPS 且主机命中平台维护的后缀名单，防止 SSRF 与凭据外发。
 */
public interface EndpointAllowlist {

    boolean isAllowed(URI endpoint);
}
