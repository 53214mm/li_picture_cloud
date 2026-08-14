package com.li.lipicturecloud.controller;

import com.li.lipicturecloud.annotation.AuthCheck;
import com.li.lipicturecloud.application.airuntime.ConnectivityResult;
import com.li.lipicturecloud.application.airuntime.CredentialService;
import com.li.lipicturecloud.application.airuntime.ModelConnectionService;
import com.li.lipicturecloud.application.airuntime.ModelConnectivityService;
import com.li.lipicturecloud.application.airuntime.ModelRoutingService;
import com.li.lipicturecloud.application.airuntime.ModelUsageService;
import com.li.lipicturecloud.application.airuntime.view.CredentialVaultView;
import com.li.lipicturecloud.application.airuntime.view.ModelConnectionView;
import com.li.lipicturecloud.application.airuntime.view.ModelRoutingRuleView;
import com.li.lipicturecloud.application.airuntime.view.ModelUsageRecordView;
import com.li.lipicturecloud.common.BaseResponse;
import com.li.lipicturecloud.common.ResultUtils;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.exception.ThrowUtils;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import com.li.lipicturecloud.model.dto.airuntime.CredentialCreateRequest;
import com.li.lipicturecloud.model.dto.airuntime.ModelConnectionCreateRequest;
import com.li.lipicturecloud.model.dto.airuntime.RotateCredentialRequest;
import com.li.lipicturecloud.model.dto.airuntime.RoutingUpdateRequest;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * 模型与 MCP 控制中心 HTTP 边界：凭据保险库、模型连接、任务路由与使用记录。
 *
 * <p>明文 API Key 只出现在创建/轮换请求体中一次；任何响应、列表或日志都不回显明文或密文。</p>
 */
@RestController
@RequestMapping(value = "/model", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "模型控制中心", description = "凭据保险库、模型连接、任务路由与使用记录")
public class ModelGatewayController {

    private final CredentialService credentialService;
    private final ModelConnectionService connectionService;
    private final ModelConnectivityService connectivityService;
    private final ModelRoutingService routingService;
    private final ModelUsageService usageService;
    private final UserService userService;

    public ModelGatewayController(CredentialService credentialService,
                                  ModelConnectionService connectionService,
                                  ModelConnectivityService connectivityService,
                                  ModelRoutingService routingService,
                                  ModelUsageService usageService,
                                  UserService userService) {
        this.credentialService = credentialService;
        this.connectionService = connectionService;
        this.connectivityService = connectivityService;
        this.routingService = routingService;
        this.usageService = usageService;
        this.userService = userService;
    }

    // ===== 凭据保险库 =====

    @PostMapping("/credentials")
    @AuthCheck
    public BaseResponse<CredentialVaultView> createCredential(@RequestBody CredentialCreateRequest body,
                                                              HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.getApiKey() == null || body.getApiKey().isBlank(),
                ErrorCode.PARAMS_ERROR, "请提供 API Key");
        return ResultUtils.success(CredentialVaultView.of(credentialService.store(
                subject(request).userId(), provider(body.getProvider()), body.getApiKey().strip())));
    }

    @GetMapping("/credentials")
    @AuthCheck
    public BaseResponse<List<CredentialVaultView>> listCredentials(HttpServletRequest request) {
        return ResultUtils.success(credentialService.list(subject(request).userId()));
    }

    @DeleteMapping("/credentials/{id}")
    @AuthCheck
    public BaseResponse<Boolean> deleteCredential(@PathVariable long id, HttpServletRequest request) {
        return ResultUtils.success(credentialService.delete(id, subject(request).userId()));
    }

    // ===== 模型连接 =====

    @PostMapping("/connections")
    @AuthCheck
    public BaseResponse<ModelConnectionView> createConnection(
            @RequestBody ModelConnectionCreateRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.getDisplayName() == null
                        || body.getDisplayName().isBlank() || body.getEndpoint() == null
                        || body.getEndpoint().isBlank() || body.getModelCode() == null
                        || body.getModelCode().isBlank(),
                ErrorCode.PARAMS_ERROR, "请完整填写连接名称、端点与模型编码");
        return ResultUtils.success(ModelConnectionView.of(connectionService.create(
                subject(request).userId(), provider(body.getProvider()), body.getDisplayName().strip(),
                parseEndpoint(body.getEndpoint()), body.getModelCode().strip(),
                body.getCredentialId())));
    }

    @GetMapping("/connections")
    @AuthCheck
    public BaseResponse<List<ModelConnectionView>> listConnections(HttpServletRequest request) {
        return ResultUtils.success(connectionService.list(subject(request).userId()).stream()
                .map(ModelConnectionView::of).toList());
    }

    @PostMapping("/connections/{id}/enable")
    @AuthCheck
    public BaseResponse<ModelConnectionView> enableConnection(@PathVariable long id,
                                                              HttpServletRequest request) {
        return ResultUtils.success(ModelConnectionView.of(
                connectionService.enable(id, subject(request).userId())));
    }

    @PostMapping("/connections/{id}/disable")
    @AuthCheck
    public BaseResponse<ModelConnectionView> disableConnection(@PathVariable long id,
                                                               HttpServletRequest request) {
        return ResultUtils.success(ModelConnectionView.of(
                connectionService.disable(id, subject(request).userId())));
    }

    @PostMapping("/connections/{id}/rotate-credential")
    @AuthCheck
    public BaseResponse<ModelConnectionView> rotateCredential(@PathVariable long id,
                                                              @RequestBody RotateCredentialRequest body,
                                                              HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.getApiKey() == null || body.getApiKey().isBlank(),
                ErrorCode.PARAMS_ERROR, "请提供新的 API Key");
        return ResultUtils.success(ModelConnectionView.of(connectionService.rotateCredential(
                id, subject(request).userId(), body.getApiKey().strip())));
    }

    @DeleteMapping("/connections/{id}")
    @AuthCheck
    public BaseResponse<Boolean> deleteConnection(@PathVariable long id, HttpServletRequest request) {
        return ResultUtils.success(connectionService.delete(id, subject(request).userId()));
    }

    @PostMapping("/connections/{id}/test")
    @AuthCheck
    public BaseResponse<ConnectivityResult> testConnection(@PathVariable long id,
                                                           HttpServletRequest request) {
        return ResultUtils.success(connectivityService.testConnection(id, subject(request).userId()));
    }

    // ===== 任务路由 =====

    @GetMapping("/routing")
    @AuthCheck
    public BaseResponse<List<ModelRoutingRuleView>> listRouting(HttpServletRequest request) {
        return ResultUtils.success(routingService.list(subject(request).userId()).stream()
                .map(ModelRoutingRuleView::of).toList());
    }

    @PutMapping("/routing/{task}")
    @AuthCheck
    public BaseResponse<ModelRoutingRuleView> upsertRouting(@PathVariable String task,
                                                            @RequestBody RoutingUpdateRequest body,
                                                            HttpServletRequest request) {
        return ResultUtils.success(ModelRoutingRuleView.of(routingService.upsert(
                subject(request).userId(), task(task), body == null ? null : body.getConnectionId())));
    }

    @DeleteMapping("/routing/{task}")
    @AuthCheck
    public BaseResponse<Boolean> deleteRouting(@PathVariable String task, HttpServletRequest request) {
        return ResultUtils.success(routingService.delete(subject(request).userId(), task(task)));
    }

    // ===== 使用记录 =====

    @GetMapping("/usage")
    @AuthCheck
    public BaseResponse<List<ModelUsageRecordView>> listUsage(@RequestParam(defaultValue = "50") int limit,
                                                              HttpServletRequest request) {
        return ResultUtils.success(usageService.listRecent(subject(request).userId(), limit).stream()
                .map(ModelUsageRecordView::of).toList());
    }

    private AuthorizationSubject subject(HttpServletRequest request) {
        User loginUser = userService.getLoginUserEntity(request);
        return AuthorizationSubject.user(loginUser.getId());
    }

    private static ModelProvider provider(String value) {
        try {
            return ModelProvider.valueOf(requireNonBlank(value, "provider"));
        } catch (IllegalArgumentException unknown) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未知的模型供应商: " + value);
        }
    }

    private static ModelTask task(String value) {
        try {
            return ModelTask.valueOf(requireNonBlank(value, "task"));
        } catch (IllegalArgumentException unknown) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未知的任务类型: " + value);
        }
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "缺少参数: " + field);
        }
        return value;
    }

    private static URI parseEndpoint(String endpoint) {
        try {
            return URI.create(endpoint.strip());
        } catch (IllegalArgumentException malformed) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "端点不是合法的 URL");
        }
    }
}
