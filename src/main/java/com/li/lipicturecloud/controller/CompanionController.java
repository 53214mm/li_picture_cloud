package com.li.lipicturecloud.controller;

import com.li.lipicturecloud.annotation.AuthCheck;
import com.li.lipicturecloud.annotation.RateLimit;
import com.li.lipicturecloud.application.companion.CompanionChatService;
import com.li.lipicturecloud.application.companion.CompanionLife;
import com.li.lipicturecloud.application.companion.CompanionMemoryService;
import com.li.lipicturecloud.application.companion.CompanionProposalService;
import com.li.lipicturecloud.application.companion.FeedPictureCommand;
import com.li.lipicturecloud.application.companion.view.ChatHistoryView;
import com.li.lipicturecloud.application.companion.view.CompanionContractView;
import com.li.lipicturecloud.application.companion.view.CompanionHomeView;
import com.li.lipicturecloud.application.companion.view.CompanionMemoryListView;
import com.li.lipicturecloud.application.companion.view.CompanionMemoryView;
import com.li.lipicturecloud.application.companion.view.CompanionProposalView;
import com.li.lipicturecloud.application.companion.view.FeedPictureResult;
import com.li.lipicturecloud.common.BaseResponse;
import com.li.lipicturecloud.common.ResultUtils;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.exception.ThrowUtils;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import com.li.lipicturecloud.model.dto.companion.CompanionChatRequest;
import com.li.lipicturecloud.model.dto.companion.CompanionContractUpdateRequest;
import com.li.lipicturecloud.model.dto.companion.CompanionFeedRequest;
import com.li.lipicturecloud.model.dto.companion.CompanionMemoryCorrectRequest;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalTime;

@RestController
@RequestMapping(value = "/companion", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "app.companion", name = "enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "图像伙伴", description = "唤醒伙伴并用授权图片获得演示成长")
/**
 * HTTP 边界只接收当前会话与图片 ID，绝不信任客户端传来的 userId。
 * 实际的图片/空间权限检查在应用层完成，控制器只负责把登录用户转换为授权主体。
 */
public class CompanionController {

    private static final Logger log = LoggerFactory.getLogger(CompanionController.class);

    private final CompanionLife companionLife;
    private final CompanionMemoryService memoryService;
    private final CompanionChatService chatService;
    private final CompanionProposalService proposalService;
    private final UserService userService;

    public CompanionController(CompanionLife companionLife, CompanionMemoryService memoryService,
                               CompanionChatService chatService, CompanionProposalService proposalService,
                               UserService userService) {
        this.companionLife = companionLife;
        this.memoryService = memoryService;
        this.chatService = chatService;
        this.proposalService = proposalService;
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

    @GetMapping("/chat/history")
    @AuthCheck
    public BaseResponse<ChatHistoryView> chatHistory(@RequestParam(defaultValue = "50") int limit,
                                                     HttpServletRequest request) {
        return ResultUtils.success(chatService.history(subject(request), limit));
    }

    @RateLimit(maxRequests = 20, windowSeconds = 60, message = "伙伴对话请求过于频繁，请稍后再试")
    @PostMapping("/chat/stream")
    @AuthCheck
    public SseEmitter chatStream(@RequestBody CompanionChatRequest body,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        ThrowUtils.throwIf(body == null || body.getMessage() == null || body.getMessage().isBlank(),
                ErrorCode.PARAMS_ERROR, "请输入想对伙伴说的话");
        response.setHeader("X-Accel-Buffering", "no");
        SseEmitter emitter = new SseEmitter(300000L);
        AuthorizationSubject subject = subject(request);
        chatService.chat(subject, body.getMessage()).subscribe(
                chunk -> {
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (IOException error) {
                        log.warn("companion_chat_sse_send_failed subjectId={}", subject.userId());
                        emitter.completeWithError(error);
                    }
                },
                error -> {
                    log.warn("companion_chat_stream_error subjectId={} exceptionType={}",
                            subject.userId(), error.getClass().getName());
                    try {
                        emitter.send(SseEmitter.event().name("error")
                                .data(chatErrorMessage(error)));
                        emitter.complete();
                    } catch (IOException ignored) {
                        emitter.completeWithError(error);
                    }
                },
                () -> {
                    try {
                        emitter.send(SseEmitter.event().name("done").data(""));
                        emitter.complete();
                    } catch (IOException error) {
                        log.warn("companion_chat_sse_done_failed subjectId={}", subject.userId());
                    }
                });
        return emitter;
    }

    @GetMapping("/contract")
    @AuthCheck
    public BaseResponse<CompanionContractView> contract(HttpServletRequest request) {
        return ResultUtils.success(proposalService.contract(subject(request)));
    }

    @PutMapping("/contract")
    @AuthCheck
    public BaseResponse<CompanionContractView> updateContract(
            @RequestBody CompanionContractUpdateRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.getActive() == null
                        || body.getQuietStart() == null || body.getQuietEnd() == null
                        || body.getMaxFrequencyHours() == null,
                ErrorCode.PARAMS_ERROR, "请完整填写主动设置");
        LocalTime quietStart = parseTime(body.getQuietStart());
        LocalTime quietEnd = parseTime(body.getQuietEnd());
        ThrowUtils.throwIf(body.getMaxFrequencyHours() < 0 || body.getMaxFrequencyHours() > 720,
                ErrorCode.PARAMS_ERROR, "频率上限需为 0-720 小时");
        return ResultUtils.success(proposalService.updateContract(subject(request),
                body.getActive(), quietStart, quietEnd, body.getMaxFrequencyHours()));
    }

    @GetMapping("/proposals/active")
    @AuthCheck
    public BaseResponse<CompanionProposalView> activeProposal(HttpServletRequest request) {
        return ResultUtils.success(proposalService.active(subject(request)));
    }

    @PostMapping("/proposals/{id}/accept")
    @AuthCheck
    public BaseResponse<CompanionProposalView> acceptProposal(@PathVariable("id") long proposalId,
                                                              HttpServletRequest request) {
        return ResultUtils.success(proposalService.accept(subject(request), proposalId));
    }

    @PostMapping("/proposals/{id}/ignore")
    @AuthCheck
    public BaseResponse<CompanionProposalView> ignoreProposal(@PathVariable("id") long proposalId,
                                                              HttpServletRequest request) {
        return ResultUtils.success(proposalService.ignore(subject(request), proposalId));
    }

    @PostMapping("/proposals/{id}/scold")
    @AuthCheck
    public BaseResponse<CompanionProposalView> scoldProposal(@PathVariable("id") long proposalId,
                                                             HttpServletRequest request) {
        return ResultUtils.success(proposalService.scold(subject(request), proposalId));
    }

    private static LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value);
        } catch (RuntimeException invalid) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "安静时段需为 HH:mm 格式");
        }
    }

    private AuthorizationSubject subject(HttpServletRequest request) {
        // 管理员身份是明确的授权能力，而不是由前端传一个 role 字段决定。
        User loginUser = userService.getLoginUserEntity(request);
        return userService.isAdmin(loginUser)
                ? AuthorizationSubject.platformAdmin(loginUser.getId())
                : AuthorizationSubject.user(loginUser.getId());
    }

    private String chatErrorMessage(Throwable error) {
        if (error instanceof com.li.lipicturecloud.application.airuntime.ModelInvocationException invocation) {
            // BYOK 失败大声暴露安全错误码对应的提示，绝不静默回退平台钱包。
            return switch (invocation.safeErrorCode()) {
                case com.li.lipicturecloud.application.airuntime.ConnectivityResult.CREDENTIAL_REJECTED ->
                        "连接凭据被模型服务拒绝，请在控制中心检查 API Key";
                case com.li.lipicturecloud.application.airuntime.ConnectivityResult.UPSTREAM_TIMEOUT ->
                        "模型服务响应超时，请稍后再试";
                default -> "模型服务暂时不可用，请稍后再试";
            };
        }
        if (error instanceof BusinessException business) {
            return business.getMessage();
        }
        return "伙伴暂时没法回应，请稍后再试";
    }
}
