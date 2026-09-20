package com.li.lipicturecloud.model.dto.airuntime;

import lombok.Data;

/**
 * 路由规则更新请求。connectionId 为空表示显式选择平台默认连接。
 */
@Data
public class RoutingUpdateRequest {
    private Long connectionId;
}
