package com.li.lipicturecloud.model.dto.airuntime;

import lombok.Data;

/**
 * 模型连接创建请求。endpoint 必须为 HTTPS 且主机命中平台白名单。
 */
@Data
public class ModelConnectionCreateRequest {
    private String provider;
    private String displayName;
    private String endpoint;
    private String modelCode;
    private Long credentialId;
}
