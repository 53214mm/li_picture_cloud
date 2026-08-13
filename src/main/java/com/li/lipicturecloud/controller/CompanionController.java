package com.li.lipicturecloud.controller;

import com.li.lipicturecloud.annotation.AuthCheck;
import com.li.lipicturecloud.application.companion.CompanionLife;
import com.li.lipicturecloud.application.companion.CompanionMemoryService;
import com.li.lipicturecloud.application.companion.FeedPictureCommand;
import com.li.lipicturecloud.application.companion.view.CompanionHomeView;
import com.li.lipicturecloud.application.companion.view.CompanionMemoryListView;
import com.li.lipicturecloud.application.companion.view.CompanionMemoryView;
import com.li.lipicturecloud.application.companion.view.FeedPictureResult;
import com.li.lipicturecloud.common.BaseResponse;
import com.li.lipicturecloud.common.ResultUtils;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.exception.ThrowUtils;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import com.li.lipicturecloud.model.dto.companion.CompanionFeedRequest;
import com.li.lipicturecloud.model.dto.companion.CompanionMemoryCorrectRequest;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/companion", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "app.companion", name = "enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "图像伙伴", description = "唤醒伙伴并用授权图片获得演示成长")
/**
 * HTTP 边界只接收当前会话与图片 ID，绝不信任客户端传来的 userId。
 * 实际的图片/空间权限检查在应用层完成，控制器只负责把登录用户转换为授权主体。
 */
public class CompanionController {

    private final CompanionLife companionLife;
    private final CompanionMemoryService memoryService;
    private final UserService userService;

    public CompanionController(CompanionLife companionLife, CompanionMemoryService memoryService,
                               UserService userService) {
        this.companionLife = companionLife;
        this.memoryService = memoryService;
        this.userService = userService;
    }

    @GetMapping("/me")
    @AuthCheck
    public BaseResponse<CompanionHomeView> current(HttpServletRequest request) {
        return ResultUtils.success(companionLife.home(subject(request)));
    }

    @PostMapping("/awaken")
    @AuthCheck
    public BaseResponse<CompanionHomeView> awaken(HttpServletRequest request) {
        return ResultUtils.success(companionLife.awaken(subject(request)));
    }

    @PostMapping("/feed")
    @AuthCheck
    public BaseResponse<FeedPictureResult> feed(@RequestBody CompanionFeedRequest feedRequest,
                                                HttpServletRequest request) {
        ThrowUtils.throwIf(feedRequest == null || feedRequest.getPictureId() == null,
                ErrorCode.PARAMS_ERROR, "请选择要喂养的图片");
        FeedPictureCommand command = new FeedPictureCommand(subject(request), feedRequest.getPictureId(),
                feedRequest.getIdempotencyKey());
        return ResultUtils.success(companionLife.feed(command));
    }

    @GetMapping("/memories")
    @AuthCheck
    public BaseResponse<CompanionMemoryListView> memories(@RequestParam(defaultValue = "50") int limit,
                                                          HttpServletRequest request) {
        return ResultUtils.success(memoryService.memories(subject(request), limit));
    }

    @PostMapping("/memories/{id}/confirm")
    @AuthCheck
    public BaseResponse<CompanionMemoryView> confirmMemory(@PathVariable("id") long memoryId,
                                                           HttpServletRequest request) {
        return ResultUtils.success(memoryService.confirm(subject(request), memoryId));
    }

    @PostMapping("/memories/{id}/correct")
    @AuthCheck
    public BaseResponse<CompanionMemoryView> correctMemory(@PathVariable("id") long memoryId,
                                                           @RequestBody CompanionMemoryCorrectRequest body,
                                                           HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.getContent() == null || body.getContent().isBlank(),
                ErrorCode.PARAMS_ERROR, "请填写纠正后的记忆内容");
        return ResultUtils.success(memoryService.correct(subject(request), memoryId, body.getContent()));
    }

    @PostMapping("/memories/{id}/dismiss")
    @AuthCheck
    public BaseResponse<CompanionMemoryView> dismissMemory(@PathVariable("id") long memoryId,
                                                           HttpServletRequest request) {
        return ResultUtils.success(memoryService.dismiss(subject(request), memoryId));
    }

    @DeleteMapping("/memories/{id}")
    @AuthCheck
    public BaseResponse<CompanionMemoryView> deleteMemory(@PathVariable("id") long memoryId,
                                                          HttpServletRequest request) {
        return ResultUtils.success(memoryService.delete(subject(request), memoryId));
    }

    private AuthorizationSubject subject(HttpServletRequest request) {
        // 管理员身份是明确的授权能力，而不是由前端传一个 role 字段决定。
        User loginUser = userService.getLoginUserEntity(request);
        return userService.isAdmin(loginUser)
                ? AuthorizationSubject.platformAdmin(loginUser.getId())
                : AuthorizationSubject.user(loginUser.getId());
    }
}
