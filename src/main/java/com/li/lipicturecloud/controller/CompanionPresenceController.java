package com.li.lipicturecloud.controller;

import com.li.lipicturecloud.annotation.AuthCheck;
import com.li.lipicturecloud.application.companion.presence.*;
import com.li.lipicturecloud.common.BaseResponse;
import com.li.lipicturecloud.common.ResultUtils;
import com.li.lipicturecloud.model.dto.companion.*;
import com.li.lipicturecloud.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/companion/presence", produces = "application/json")
@ConditionalOnProperty(prefix = "app.companion", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CompanionPresenceController {
    private final CompanionPresenceService service;
    private final UserService users;
    public CompanionPresenceController(CompanionPresenceService service, UserService users) {
        this.service = service; this.users = users;
    }
    /** Invalid extra fields are rejected without logging their potentially sensitive values. */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public org.springframework.http.ResponseEntity<BaseResponse<?>> invalidBody() {
        return org.springframework.http.ResponseEntity.badRequest().body(ResultUtils.error(40000, "主动事件参数无效"));
    }
    @AuthCheck
    @PostMapping("/session")
    public BaseResponse<PresenceSessionView> open(@RequestBody PresenceSessionRequest body, HttpServletRequest request) {
        return ResultUtils.success(service.open(users.getLoginUserEntity(request).getId(), body));
    }
    @AuthCheck
    @PostMapping("/decide")
    public BaseResponse<PresenceDecisionView> decide(@RequestBody PresenceEventRequest body, HttpServletRequest request) {
        return ResultUtils.success(service.decide(users.getLoginUserEntity(request).getId(), body));
    }
    @AuthCheck
    @DeleteMapping("/session/{sessionId}")
    public BaseResponse<Boolean> close(@PathVariable String sessionId, HttpServletRequest request) {
        return ResultUtils.success(service.close(users.getLoginUserEntity(request).getId(), sessionId));
    }
}
