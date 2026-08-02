package com.li.lipicturecloud.controller;

import com.li.lipicturecloud.AI.app.PicCloudApp;
import com.li.lipicturecloud.AI.chatMemory.RedisBasedChatMemory;
import com.li.lipicturecloud.AI.AiRequestIntent;
import com.li.lipicturecloud.annotation.AuthCheck;
import com.li.lipicturecloud.annotation.RateLimit;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/ai")
public class AIController {

    @Resource
    private PicCloudApp picCloudApp;
    @Resource
    private UserService userService;
    @Resource
    private RedisBasedChatMemory chatMemory;

    /** 用户消息最大长度 */
    private static final int MAX_MESSAGE_LENGTH = 2000;

    @AuthCheck
    @RateLimit(maxRequests = 15, windowSeconds = 60, message = "AI 对话请求过于频繁，请稍后再试")
    @GetMapping("/chat/stream")
    public SseEmitter chatStream(@RequestParam String message,
                                  @RequestParam(defaultValue = "piccloud-chat") String chatId,
                                  HttpServletRequest request,
                                  HttpServletResponse response) {
        // ★ 输入长度校验
        if (message != null && message.length() > MAX_MESSAGE_LENGTH) {
            response.setStatus(400);
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event().data("消息过长，最多 " + MAX_MESSAGE_LENGTH + " 个字符"));
                errorEmitter.complete();
            } catch (IOException e) { errorEmitter.completeWithError(e); }
            return errorEmitter;
        }

        User loginUser = userService.getLoginUserEntity(request);
        // SSE 只能 GET，用 Origin 校验防 CSRF
        String origin = request.getHeader("Origin");
        if (origin != null) {
            String host = request.getHeader("Host");
            if (host == null) {
                response.setStatus(403); return null;
            }
            // ★ 从 Origin 中提取 host 部分做精确比较
            try {
                String originHost = new java.net.URL(origin).getHost();
                if (!host.equals(originHost)) {
                    log.warn("[CSRF拦截] Origin 不匹配: origin={}, host={}", origin, host);
                    response.setStatus(403); return null;
                }
            } catch (Exception e) {
                log.warn("[CSRF拦截] 无法解析 Origin: {}", origin);
                response.setStatus(403); return null;
            }
        }
        response.setHeader("X-Accel-Buffering", "no");
        String scopedChatId = loginUser.getId() + ":" + chatId;

        SseEmitter emitter = new SseEmitter(480000L);
        // 只有明确的生图请求才提示生成耗时，普通聊天不应出现误导性文案。
        if (AiRequestIntent.isImageGenerationRequest(message)) {
            try {
                emitter.send(SseEmitter.event().data("正在处理您的请求，图片生成可能需要 1-2 分钟...\n\n"));
            } catch (IOException ignored) {}
        }
        picCloudApp.doChatStream(message, scopedChatId, loginUser).subscribe(
            chunk -> {
                try { emitter.send(SseEmitter.event().data(chunk)); } catch (IOException e) {
                    log.error("SSE send failed", e);
                    try { emitter.completeWithError(e); } catch (Exception ignored) {}
                }
            },
            ex -> {
                log.error("AI stream error for user {}", loginUser.getId(), ex);
                try { emitter.completeWithError(new RuntimeException("AI 服务异常，请稍后重试")); } catch (Exception ignored) {}
            },
            () -> {
                try { emitter.send(SseEmitter.event().name("done").data("")); emitter.complete(); }
                catch (IOException e) { log.error("SSE done failed", e); }
            }
        );
        return emitter;
    }

    /** ★ 清空对话记忆 */
    @AuthCheck
    @PostMapping("/chat/clear")
    public String clearChat(@RequestParam(defaultValue = "piccloud-chat") String chatId,
                            HttpServletRequest request) {
        User loginUser = userService.getLoginUserEntity(request);
        String scopedChatId = loginUser.getId() + ":" + chatId;
        chatMemory.clear(scopedChatId);
        log.info("用户 {} 清空了对话 {}", loginUser.getId(), chatId);
        return "ok";
    }
}
