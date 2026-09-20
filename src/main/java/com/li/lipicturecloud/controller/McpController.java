package com.li.lipicturecloud.controller;

import com.li.lipicturecloud.annotation.AuthCheck;
import com.li.lipicturecloud.application.airuntime.McpConnectionService;
import com.li.lipicturecloud.application.airuntime.view.McpServiceView;
import com.li.lipicturecloud.application.airuntime.view.McpToolWhitelistView;
import com.li.lipicturecloud.common.BaseResponse;
import com.li.lipicturecloud.common.ResultUtils;
import com.li.lipicturecloud.constant.UserConstant;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.exception.ThrowUtils;
import com.li.lipicturecloud.model.dto.airuntime.McpServiceRequest;
import com.li.lipicturecloud.model.dto.airuntime.McpToolRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MCP 白名单管理面（仅平台管理员）：服务登记/启停 + 工具白名单增删/启停。
 * 未在此登记的工具不会进入伙伴能力目录（fail-closed）。
 */
@RestController
@RequestMapping(value = "/model/mcp", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "模型控制中心", description = "MCP 服务白名单与逐项启停")
public class McpController {

    private final McpConnectionService mcpConnectionService;

    public McpController(McpConnectionService mcpConnectionService) {
        this.mcpConnectionService = mcpConnectionService;
    }

    @GetMapping("/services")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<List<McpServiceView>> listServices() {
        return ResultUtils.success(mcpConnectionService.listServices().stream()
                .map(McpServiceView::of).toList());
    }

    @PostMapping("/services")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<McpServiceView> upsertService(@RequestBody McpServiceRequest body) {
        ThrowUtils.throwIf(body == null || body.getCode() == null || body.getCode().isBlank()
                        || body.getDisplayName() == null || body.getDisplayName().isBlank()
                        || body.getEndpointUri() == null || body.getEndpointUri().isBlank(),
                ErrorCode.PARAMS_ERROR, "请完整填写服务代码、名称与端点");
        try {
            return ResultUtils.success(McpServiceView.of(mcpConnectionService.upsertService(
                    body.getCode().strip(), body.getDisplayName().strip(),
                    body.getEndpointUri().strip())));
        } catch (IllegalArgumentException malformed) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "MCP 服务参数不合法: "
                    + malformed.getMessage());
        }
    }

    @PostMapping("/services/{code}/enable")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<McpServiceView> enableService(@PathVariable String code) {
        return ResultUtils.success(McpServiceView.of(mcpConnectionService.enableService(code)));
    }

    @PostMapping("/services/{code}/disable")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<McpServiceView> disableService(@PathVariable String code) {
        return ResultUtils.success(McpServiceView.of(mcpConnectionService.disableService(code)));
    }

    @GetMapping("/services/{code}/tools")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<List<McpToolWhitelistView>> listTools(@PathVariable String code) {
        return ResultUtils.success(mcpConnectionService.listTools(code).stream()
                .map(McpToolWhitelistView::of).toList());
    }

    @PostMapping("/services/{code}/tools")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<McpToolWhitelistView> addTool(@PathVariable String code,
                                                      @RequestBody McpToolRequest body) {
        ThrowUtils.throwIf(body == null || body.getToolName() == null || body.getToolName().isBlank(),
                ErrorCode.PARAMS_ERROR, "请提供工具名");
        return ResultUtils.success(McpToolWhitelistView.of(mcpConnectionService.addTool(
                code, body.getToolName().strip())));
    }

    @PostMapping("/services/{code}/tools/{toolName}/enable")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<McpToolWhitelistView> enableTool(@PathVariable String code,
                                                         @PathVariable String toolName) {
        return ResultUtils.success(McpToolWhitelistView.of(mcpConnectionService.enableTool(
                code, toolName)));
    }

    @PostMapping("/services/{code}/tools/{toolName}/disable")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<McpToolWhitelistView> disableTool(@PathVariable String code,
                                                          @PathVariable String toolName) {
        return ResultUtils.success(McpToolWhitelistView.of(mcpConnectionService.disableTool(
                code, toolName)));
    }

    @DeleteMapping("/services/{code}/tools/{toolName}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> removeTool(@PathVariable String code,
                                            @PathVariable String toolName) {
        return ResultUtils.success(mcpConnectionService.removeTool(code, toolName));
    }
}
