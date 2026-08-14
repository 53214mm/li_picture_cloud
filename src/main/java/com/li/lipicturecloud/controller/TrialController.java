package com.li.lipicturecloud.controller;

import com.li.lipicturecloud.annotation.AuthCheck;
import com.li.lipicturecloud.application.airuntime.PlatformTrialLedgerService;
import com.li.lipicturecloud.application.airuntime.view.TrialBalanceView;
import com.li.lipicturecloud.common.BaseResponse;
import com.li.lipicturecloud.common.ResultUtils;
import com.li.lipicturecloud.constant.UserConstant;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.exception.ThrowUtils;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import com.li.lipicturecloud.model.dto.airuntime.TrialGrantRequest;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台试用额度：用户查询自己的可用额度；管理员查询任意主体并授予额度。
 * 试用额度是硬上限：超限停止，不自动扣费。
 */
@RestController
@RequestMapping(value = "/model/trial", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "模型控制中心", description = "平台试用额度账本")
public class TrialController {

    private final PlatformTrialLedgerService trialLedgerService;
    private final UserService userService;

    public TrialController(PlatformTrialLedgerService trialLedgerService, UserService userService) {
        this.trialLedgerService = trialLedgerService;
        this.userService = userService;
    }

    @GetMapping("/me")
    @AuthCheck
    public BaseResponse<TrialBalanceView> mine(HttpServletRequest request) {
        return ResultUtils.success(TrialBalanceView.of(
                trialLedgerService.getOrCreate(subject(request).userId())));
    }

    @GetMapping("/{subjectId}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<TrialBalanceView> bySubject(@PathVariable long subjectId) {
        return ResultUtils.success(TrialBalanceView.of(
                trialLedgerService.getOrCreate(subjectId)));
    }

    @PostMapping("/grant")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<TrialBalanceView> grant(@RequestBody TrialGrantRequest body) {
        ThrowUtils.throwIf(body == null || body.getSubjectId() == null || body.getSubjectId() <= 0
                        || body.getAmount() == null || body.getAmount() <= 0,
                ErrorCode.PARAMS_ERROR, "请提供有效的主体 ID 与正数额度");
        return ResultUtils.success(TrialBalanceView.of(
                trialLedgerService.grant(body.getSubjectId(), body.getAmount())));
    }

    private AuthorizationSubject subject(HttpServletRequest request) {
        User loginUser = userService.getLoginUserEntity(request);
        return AuthorizationSubject.user(loginUser.getId());
    }
}
