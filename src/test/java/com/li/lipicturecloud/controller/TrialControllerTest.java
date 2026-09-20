package com.li.lipicturecloud.controller;

import com.li.lipicturecloud.application.airuntime.PlatformTrialLedgerService;
import com.li.lipicturecloud.domain.airuntime.PlatformTrialLedger;
import com.li.lipicturecloud.exception.GlobalExceptionHandler;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TrialControllerTest {

    private MockMvc mockMvc;
    private PlatformTrialLedgerService trialLedgerService;

    @BeforeEach
    void setUp() {
        trialLedgerService = mock(PlatformTrialLedgerService.class);
        UserService userService = mock(UserService.class);
        User loginUser = new User();
        loginUser.setId(7L);
        when(userService.getLoginUserEntity(any(HttpServletRequest.class))).thenReturn(loginUser);
        TrialController controller = new TrialController(trialLedgerService, userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mineReturnsTheSafeBalanceView() throws Exception {
        when(trialLedgerService.getOrCreate(7L))
                .thenReturn(PlatformTrialLedger.restore(3L, 7L, 100L, 10L, 4L));

        mockMvc.perform(get("/model/trial/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(100))
                .andExpect(jsonPath("$.data.reserved").value(10))
                .andExpect(jsonPath("$.data.available").value(90));
    }

    @Test
    void grantValidatesAndAppliesThroughTheService() throws Exception {
        when(trialLedgerService.grant(7L, 50L))
                .thenReturn(PlatformTrialLedger.restore(3L, 7L, 150L, 0L, 5L));

        mockMvc.perform(post("/model/trial/grant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":7,\"amount\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(150));

        mockMvc.perform(post("/model/trial/grant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":7,\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }
}
