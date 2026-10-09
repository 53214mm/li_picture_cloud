package com.li.lipicturecloud.controller;

import com.li.lipicturecloud.annotation.AuthCheck;
import com.li.lipicturecloud.annotation.RateLimit;
import com.li.lipicturecloud.application.airuntime.EndpointAllowlist;
import com.li.lipicturecloud.application.airuntime.ModelProviderPresets;
import com.li.lipicturecloud.application.airuntime.ModelQuickSetupService;
import com.li.lipicturecloud.application.airuntime.view.ModelDiscoveryView;
import com.li.lipicturecloud.application.airuntime.view.QuickConnectionView;
import com.li.lipicturecloud.application.companion.CompanionChatModeService;
import com.li.lipicturecloud.common.BaseResponse;
import com.li.lipicturecloud.common.ResultUtils;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.companion.CompanionChatMode;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.model.dto.airuntime.QuickConnectRequest;
import com.li.lipicturecloud.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 模型快速连接：选厂商 → 填 API Key → 选模型，其余由平台完成。
 * 完整的连接、凭据与任务路由管理仍在 {@link ModelGatewayController}。
 */
@RestController
@RequestMapping(value = "/model/quick", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "模型快速连接", description = "选厂商、填 Key、选模型的一步连接")
public class ModelQuickSetupController {

    private static final Logger log = LoggerFactory.getLogger(ModelQuickSetupController.class);

    private final ModelQuickSetupService quickSetupService;
    private final CompanionChatModeService chatModeService;
    private final EndpointAllowlist allowlist;
    private final UserService userService;

    public ModelQuickSetupController(ModelQuickSetupService quickSetupService,
                                     CompanionChatModeService chatModeService,
                                     EndpointAllowlist allowlist,
                                     UserService userService) {
        this.quickSetupService = quickSetupService;
        this.chatModeService = chatModeService;
        this.allowlist = allowlist;
        this.userService = userService;
    }

    /** 平台开放的厂商（端点命中白名单）及推荐模型。 */
    public record ProviderOption(ModelProvider provider, String label, String recommendedModel) {
    }

    @GetMapping("/providers")
    @AuthCheck
    public BaseResponse<List<ProviderOption>> providers() {
        return ResultUtils.success(Arrays.stream(ModelProvider.values())
                .map(ModelProviderPresets::of)
                .filter(preset -> allowlist.isAllowed(preset.endpoint()))
                .map(preset -> new ProviderOption(preset.provider(), preset.label(),
                        preset.recommendedModel()))
                .toList());
    }

    @RateLimit(maxRequests = 10, windowSeconds = 60, message = "获取模型列表过于频繁，请稍后再试")
    @PostMapping("/models")
    @AuthCheck
    public BaseResponse<ModelDiscoveryView> models(@RequestBody QuickConnectRequest body,
                                                   HttpServletRequest request) {
        requireBody(body);
        return ResultUtils.success(quickSetupService.discover(userId(request),
                provider(body.getProvider()), body.getApiKey()));
    }

    @RateLimit(maxRequests = 10, windowSeconds = 60, message = "连接请求过于频繁，请稍后再试")
    @PostMapping("/connect")
    @AuthCheck
    public BaseResponse<QuickConnectionView> connect(@RequestBody QuickConnectRequest body,
                                                     HttpServletRequest request) {
        requireBody(body);
        long userId = userId(request);
        QuickConnectionView connected = quickSetupService.connect(userId, provider(body.getProvider()),
                body.getApiKey(), body.getModelCode());
        selectModelChat(userId);
        return ResultUtils.success(connected);
    }

    @RateLimit(maxRequests = 10, windowSeconds = 60, message = "连接请求过于频繁，请稍后再试")
    @PostMapping("/connections/{id}/use")
    @AuthCheck
    public BaseResponse<QuickConnectionView> useExisting(@PathVariable long id,
                                                         HttpServletRequest request) {
        long userId = userId(request);
        QuickConnectionView connected = quickSetupService.useExisting(userId, id);
        selectModelChat(userId);
        return ResultUtils.success(connected);
    }

    private void selectModelChat(long userId) {
        // 连上自己的模型后，伙伴聊天直接改用它；切换失败不影响已建好的连接。
        try {
            chatModeService.choose(userId, CompanionChatMode.MODEL);
        } catch (RuntimeException modeFailure) {
            log.warn("model_quick_setup_chat_mode_not_switched subjectId={}", userId);
        }
    }

    @GetMapping("/current")
    @AuthCheck
    public BaseResponse<QuickConnectionView> current(HttpServletRequest request) {
        return ResultUtils.success(quickSetupService.current(userId(request)));
    }

    @DeleteMapping("/current")
    @AuthCheck
    public BaseResponse<QuickConnectionView> usePlatform(HttpServletRequest request) {
        return ResultUtils.success(quickSetupService.usePlatform(userId(request)));
    }

    private long userId(HttpServletRequest request) {
        return userService.getLoginUserEntity(request).getId();
    }

    private static void requireBody(QuickConnectRequest body) {
        if (body == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请选择厂商并填写 API Key");
        }
    }

    private static ModelProvider provider(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请选择厂商");
        }
        try {
            return ModelProvider.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未知的模型厂商: " + value);
        }
    }
}
