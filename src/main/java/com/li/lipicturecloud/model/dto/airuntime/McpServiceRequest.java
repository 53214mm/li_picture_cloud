package com.li.lipicturecloud.model.dto.airuntime;

import lombok.Data;

/**
 * MCP 服务登记请求（平台管理面）。endpoint 必须为无 userinfo/query/fragment 的 HTTPS URL。
 */
@Data
public class McpServiceRequest {
    private String code;
    private String displayName;
    private String endpointUri;
}
