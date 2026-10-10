package com.li.lipicturecloud.controller;

import com.li.lipicturecloud.application.airuntime.EndpointAllowlist;
import com.li.lipicturecloud.application.airuntime.ModelQuickSetupService;
import com.li.lipicturecloud.application.airuntime.view.QuickConnectionView;
import com.li.lipicturecloud.application.companion.CompanionChatModeService;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.companion.CompanionChatMode;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.exception.GlobalExceptionHandler;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ModelQuickSetupControllerTest {
    @Test
    void selectingSavedConnectionUsesAuthenticatedIdentityAndSwitchesChatMode() throws Exception {
        var quick = mock(ModelQuickSetupService.class);
        var modes = mock(CompanionChatModeService.class);
        var users = mock(UserService.class);
        var user = new User();
        user.setId(7L);
        when(users.getLoginUserEntity(any())).thenReturn(user);
        long connectionId = 2056693890067525634L;
        when(quick.useExisting(7L, connectionId)).thenReturn(new QuickConnectionView(
                QuickConnectionView.Status.CONNECTED, ModelProvider.DEEPSEEK, "DeepSeek", "deepseek-flash", connectionId));
        var mvc = MockMvcBuilders.standaloneSetup(new ModelQuickSetupController(quick, modes,
                        mock(EndpointAllowlist.class), users,
                        mock(com.li.lipicturecloud.application.airuntime.VisionSearchPreferenceService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/model/quick/connections/" + connectionId + "/use"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.modelCode").value("deepseek-flash"));
        verify(quick).useExisting(7L, connectionId);
        verify(modes).choose(7L, CompanionChatMode.MODEL);
        when(quick.useExisting(7L, connectionId)).thenThrow(new BusinessException(ErrorCode.OPERATION_ERROR, "验证失败"));
        clearInvocations(modes);
        mvc.perform(post("/model/quick/connections/" + connectionId + "/use"))
                .andExpect(jsonPath("$.message").value("验证失败"));
        verifyNoInteractions(modes);
    }
}
