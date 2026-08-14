package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.McpConnection;
import com.li.lipicturecloud.domain.airuntime.McpConnectionRepository;
import com.li.lipicturecloud.domain.airuntime.McpToolWhitelist;
import com.li.lipicturecloud.domain.airuntime.McpToolWhitelistRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * MCP 白名单管理服务（平台管理面）：服务增删/启停 + 工具白名单增删/启停。
 * 所有写操作走 revision CAS；调用方（管理 API）负责管理员鉴权。
 * 任何写操作后使 MCP 工具缓存失效，让裁决立即生效。
 */
@Service
public class McpConnectionService {

    private final McpConnectionRepository connectionRepository;
    private final McpToolWhitelistRepository whitelistRepository;
    private final ObjectProvider<McpToolCacheInvalidator> cacheInvalidator;

    public McpConnectionService(McpConnectionRepository connectionRepository,
                                McpToolWhitelistRepository whitelistRepository,
                                ObjectProvider<McpToolCacheInvalidator> cacheInvalidator) {
        this.connectionRepository = connectionRepository;
        this.whitelistRepository = whitelistRepository;
        this.cacheInvalidator = cacheInvalidator;
    }

    private void invalidateToolCache() {
        McpToolCacheInvalidator invalidator = cacheInvalidator.getIfAvailable();
        if (invalidator != null) {
            invalidator.invalidateToolCache();
        }
    }

    public List<McpConnection> listServices() {
        return connectionRepository.findAll();
    }

    public McpConnection upsertService(String code, String displayName, String endpointUri) {
        Objects.requireNonNull(code, "code");
        McpConnection result = connectionRepository.findByCode(code)
                .map(existing -> existing)
                .orElseGet(() -> connectionRepository.insert(McpConnection.create(
                        code, displayName, URI.create(endpointUri))));
        invalidateToolCache();
        return result;
    }

    public McpConnection enableService(String code) {
        McpConnection connection = requireService(code);
        McpConnection result = saveOrConflict(connection.enable(), connection.revision());
        invalidateToolCache();
        return result;
    }

    public McpConnection disableService(String code) {
        McpConnection connection = requireService(code);
        McpConnection result = saveOrConflict(connection.disable(), connection.revision());
        invalidateToolCache();
        return result;
    }

    public List<McpToolWhitelist> listTools(String code) {
        return whitelistRepository.findByConnectionId(requireService(code).id());
    }

    public McpToolWhitelist addTool(String code, String toolName) {
        McpConnection connection = requireService(code);
        Objects.requireNonNull(toolName, "toolName");
        McpToolWhitelist result = whitelistRepository
                .findByConnectionAndTool(connection.id(), toolName)
                .orElseGet(() -> whitelistRepository.insert(McpToolWhitelist.create(
                        connection.id(), toolName)));
        invalidateToolCache();
        return result;
    }

    public McpToolWhitelist enableTool(String code, String toolName) {
        McpToolWhitelist entry = requireTool(code, toolName);
        McpToolWhitelist result = saveToolOrConflict(entry.enable(), entry.revision());
        invalidateToolCache();
        return result;
    }

    public McpToolWhitelist disableTool(String code, String toolName) {
        McpToolWhitelist entry = requireTool(code, toolName);
        McpToolWhitelist result = saveToolOrConflict(entry.disable(), entry.revision());
        invalidateToolCache();
        return result;
    }

    public boolean removeTool(String code, String toolName) {
        McpToolWhitelist entry = requireTool(code, toolName);
        if (!whitelistRepository.delete(entry.id(), entry.revision())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "白名单发生并发冲突，请重试");
        }
        invalidateToolCache();
        return true;
    }

    private McpConnection requireService(String code) {
        return connectionRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "MCP 服务不存在"));
    }

    private McpToolWhitelist requireTool(String code, String toolName) {
        McpConnection connection = requireService(code);
        return whitelistRepository.findByConnectionAndTool(connection.id(), toolName)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "白名单工具不存在"));
    }

    private McpConnection saveOrConflict(McpConnection after, long expectedRevision) {
        if (!connectionRepository.save(after, expectedRevision)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "MCP 服务发生并发冲突，请重试");
        }
        return after;
    }

    private McpToolWhitelist saveToolOrConflict(McpToolWhitelist after, long expectedRevision) {
        if (!whitelistRepository.save(after, expectedRevision)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "白名单发生并发冲突，请重试");
        }
        return after;
    }
}
