package com.li.lipicturecloud.infrastructure.airuntime;

import com.li.lipicturecloud.application.airuntime.McpToolAccessDecider;
import com.li.lipicturecloud.domain.airuntime.McpConnection;
import com.li.lipicturecloud.domain.airuntime.McpConnectionRepository;
import com.li.lipicturecloud.domain.airuntime.McpToolWhitelist;
import com.li.lipicturecloud.domain.airuntime.McpToolWhitelistRepository;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 基于数据库白名单的工具访问裁决：服务缺失/停用、工具未入白名单或停用，
 * 一律拒绝（fail-closed）。任意未审核 URL 不存在于平台配置中，天然不可达。
 */
@Component
public class DbMcpToolAccessDecider implements McpToolAccessDecider {

    private final McpConnectionRepository connectionRepository;
    private final McpToolWhitelistRepository whitelistRepository;

    public DbMcpToolAccessDecider(McpConnectionRepository connectionRepository,
                                  McpToolWhitelistRepository whitelistRepository) {
        this.connectionRepository = connectionRepository;
        this.whitelistRepository = whitelistRepository;
    }

    @Override
    public boolean isToolAllowed(String serviceCode, String toolName) {
        Objects.requireNonNull(serviceCode, "serviceCode");
        Objects.requireNonNull(toolName, "toolName");
        McpConnection connection = connectionRepository.findByCode(serviceCode).orElse(null);
        if (connection == null || !connection.enabled()) {
            return false;
        }
        McpToolWhitelist entry = whitelistRepository
                .findByConnectionAndTool(connection.id(), toolName).orElse(null);
        return entry != null && entry.enabled();
    }
}
