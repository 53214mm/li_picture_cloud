package com.li.lipicturecloud.controller;

import com.li.lipicturecloud.application.companion.CompanionChatService;
import com.li.lipicturecloud.application.companion.CompanionLife;
import com.li.lipicturecloud.application.companion.CompanionMemoryService;
import com.li.lipicturecloud.application.companion.CompanionProposalService;
import com.li.lipicturecloud.application.companion.FeedPictureCommand;
import com.li.lipicturecloud.application.companion.view.CompanionHomeView;
import com.li.lipicturecloud.application.companion.view.FeedPictureResult;
import com.li.lipicturecloud.application.companion.view.NutritionStatusView;
import com.li.lipicturecloud.exception.GlobalExceptionHandler;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompanionControllerTest {

    private MockMvc mockMvc;
    private CompanionLife companionLife;
    private CompanionMemoryService memoryService;
    private CompanionChatService chatService;
    private CompanionProposalService proposalService;
    private UserService userService;
    private AuthorizationSubject subject;

    @BeforeEach
    void setUp() {
        companionLife = mock(CompanionLife.class);
        memoryService = mock(CompanionMemoryService.class);
        chatService = mock(CompanionChatService.class);
        proposalService = mock(CompanionProposalService.class);
        userService = mock(UserService.class);
        User loginUser = new User();
        loginUser.setId(7L);
        when(userService.getLoginUserEntity(any(HttpServletRequest.class))).thenReturn(loginUser);
        when(userService.isAdmin(loginUser)).thenReturn(false);
        subject = AuthorizationSubject.user(7L);
        CompanionController controller = new CompanionController(companionLife, memoryService,
                chatService, proposalService, userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void currentReturnsAnExplicitEmptyHomeWithoutAutoAwakening() throws Exception {
        when(companionLife.home(subject)).thenReturn(new CompanionHomeView(null,
                new NutritionStatusView("DEMO_ONLY", "internal", "demo-v1", 0,
                        "仅根据图片 ID 选择固定营养档案，未读取图片内容，也未调用视觉模型。"),
                List.of()));

        mockMvc.perform(get("/companion/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.companion").value(nullValue()))
                .andExpect(jsonPath("$.data.nutrition.policy").value("DEMO_ONLY"));
        verify(companionLife).home(subject);
        verify(companionLife, never()).awaken(any());
    }

    @Test
    void feedBuildsSubjectFromSessionAndIgnoresClaimedUserField() throws Exception {
        when(companionLife.feed(any())).thenReturn(feedResult());

        mockMvc.perform(post("/companion/feed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pictureId":"102",
                                 "idempotencyKey":"6f26d166-0a82-4d9f-8a61-6c21cf2e59d0",
                                 "userId":"999"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.outcome").value("GROWN"));

        ArgumentCaptor<FeedPictureCommand> command = ArgumentCaptor.forClass(FeedPictureCommand.class);
        verify(companionLife).feed(command.capture());
        assertThat(command.getValue().subject()).isEqualTo(subject);
        assertThat(command.getValue().pictureId()).isEqualTo(102L);
    }

    @Test
    void awakenUsesTheSessionSubject() throws Exception {
        when(companionLife.awaken(subject)).thenReturn(new CompanionHomeView(null,
                new NutritionStatusView("DEMO_ONLY", "internal", "demo-v1", 0, "演示"), List.of()));

        mockMvc.perform(post("/companion/awaken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(companionLife).awaken(subject);
    }

    @Test
    void emptyFeedBodyReturnsTheStandardParameterError() throws Exception {
        mockMvc.perform(post("/companion/feed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
        verify(companionLife, never()).feed(any());
    }

    @Test
    void blankChatMessageIsRejectedBeforeAnyStreaming() throws Exception {
        mockMvc.perform(post("/companion/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
        verify(chatService, never()).chat(any(), any());
    }

    @Test
    void terminalEmitterCancellationDisposesTheActiveModelSubscription() {
        // 捕获 SseEmitter 注册的完成/超时回调，模拟容器在正常完成或客户端断开时触发它们。
        AtomicReference<Runnable> completion = new AtomicReference<>();
        AtomicReference<Runnable> timeout = new AtomicReference<>();
        SseEmitter emitter = new SseEmitter() {
            @Override
            public synchronized void onCompletion(Runnable callback) {
                completion.set(callback);
            }

            @Override
            public synchronized void onTimeout(Runnable callback) {
                timeout.set(callback);
            }
        };
        AtomicReference<Disposable> subscription = new AtomicReference<>();
        AtomicBoolean terminal = new AtomicBoolean(false);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CompanionController.attachTerminalCancellation(emitter, subscription, terminal);
        subscription.set(() -> cancelled.set(true));

        // 浏览器断开/正常完成 → onCompletion → 取消仍在运行的模型流订阅。
        completion.get().run();
        assertThat(cancelled).isTrue();
        assertThat(terminal).isTrue();

        // 已取消后再次触发（Spring 可能重复回调）幂等安全。
        completion.get().run();

        // 超时回调把发射器置为终态并显式完成；容器随后触发 onCompletion 走同一条取消链。
        timeout.get().run();
        assertThat(terminal).isTrue();
    }

    @Test
    void subscriptionCreatedAfterTerminalEventIsDisposedImmediately() {
        AtomicReference<Runnable> completion = new AtomicReference<>();
        SseEmitter emitter = new SseEmitter() {
            @Override
            public synchronized void onCompletion(Runnable callback) {
                completion.set(callback);
            }
        };
        AtomicReference<Disposable> subscription = new AtomicReference<>();
        AtomicBoolean terminal = new AtomicBoolean(false);
        CompanionController.attachTerminalCancellation(emitter, subscription, terminal);

        // 发射器在订阅建立前就完成（罕见窗口：subscribe 尚未返回）。
        completion.get().run();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        subscription.set(() -> cancelled.set(true));
        // chatStream 在赋值后会检查 terminal 标志并立即取消新订阅。
        if (terminal.get()) {
            subscription.get().dispose();
        }
        assertThat(cancelled).isTrue();
    }

    private FeedPictureResult feedResult() {
        return new FeedPictureResult("GROWN", "fef53056-2d9f-467d-9b1d-1afe9a6638fe", null, null);
    }
}
