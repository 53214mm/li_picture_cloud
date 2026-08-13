package com.li.lipicturecloud.controller;

import com.li.lipicturecloud.application.companion.CompanionLife;
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

import java.util.List;

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
    private UserService userService;
    private AuthorizationSubject subject;

    @BeforeEach
    void setUp() {
        companionLife = mock(CompanionLife.class);
        userService = mock(UserService.class);
        User loginUser = new User();
        loginUser.setId(7L);
        when(userService.getLoginUserEntity(any(HttpServletRequest.class))).thenReturn(loginUser);
        when(userService.isAdmin(loginUser)).thenReturn(false);
        subject = AuthorizationSubject.user(7L);
        CompanionController controller = new CompanionController(companionLife, userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void currentReturnsAnExplicitEmptyHomeWithoutAutoAwakening() throws Exception {
        when(companionLife.home(subject)).thenReturn(new CompanionHomeView(null,
                new NutritionStatusView("DEMO_DETERMINISTIC", false,
                        "仅根据图片 ID 选择固定营养档案，未读取图片内容，也未调用视觉模型。"),
                List.of()));

        mockMvc.perform(get("/companion/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.companion").value(nullValue()))
                .andExpect(jsonPath("$.data.nutrition.contentUnderstood").value(false));
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
                new NutritionStatusView("DEMO_DETERMINISTIC", false, "演示"), List.of()));

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

    private FeedPictureResult feedResult() {
        return new FeedPictureResult("GROWN", "fef53056-2d9f-467d-9b1d-1afe9a6638fe", null, null);
    }
}
