package com.li.lipicturecloud.model.dto.airuntime;

import lombok.Data;

/**
 * 快速连接请求。apiKey 只在本次请求内存中出现：读取模型列表时不保存，确认连接后才加密落库。
 * 读取模型列表时 modelCode 为空。
 */
@Data
public class QuickConnectRequest {
    private String provider;
    private String apiKey;
    private String modelCode;
}
