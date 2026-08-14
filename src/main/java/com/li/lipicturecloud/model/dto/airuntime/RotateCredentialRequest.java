package com.li.lipicturecloud.model.dto.airuntime;

import lombok.Data;

/**
 * 凭据轮换请求：用新明文替换连接当前凭据。
 */
@Data
public class RotateCredentialRequest {
    private String apiKey;
}
