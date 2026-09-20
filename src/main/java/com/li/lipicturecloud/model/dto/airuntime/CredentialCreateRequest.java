package com.li.lipicturecloud.model.dto.airuntime;

import lombok.Data;

/**
 * 凭据创建请求。apiKey 只在本次请求内存中出现，落库前即加密。
 */
@Data
public class CredentialCreateRequest {
    private String provider;
    private String apiKey;
}
