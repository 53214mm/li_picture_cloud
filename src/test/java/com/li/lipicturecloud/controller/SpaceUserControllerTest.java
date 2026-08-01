package com.li.lipicturecloud.controller;

import com.li.lipicturecloud.common.BaseResponse;
import com.li.lipicturecloud.common.DeleteRequest;
import com.li.lipicturecloud.exception.GlobalExceptionHandler;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.model.dto.spaceuser.SpaceUserEditRequest;
import com.li.lipicturecloud.service.SpaceUserService;
import com.li.lipicturecloud.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpaceUserControllerTest {

    private MockMvc mockMvc;
    private SpaceAuthorizationAccessService accessService;
    private SpaceUserService spaceUserService;
    private SpaceUserController controller;
    private HttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        accessService = mock(SpaceAuthorizationAccessService.class);
        spaceUserService = mock(SpaceUserService.class);
        httpRequest = mock(HttpServletRequest.class);
        controller = new SpaceUserController();
        ReflectionTestUtils.setField(controller, "authorizationAccessService", accessService);
        ReflectionTestUtils.setField(controller, "spaceUserService", spaceUserService);
        ReflectionTestUtils.setField(controller, "userService", mock(UserService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsServerCalculatedPermissions() throws Exception {
        when(accessService.getPermissions(eq(7L), isNull(), isNull(), any(HttpServletRequest.class)))
                .thenReturn(Set.of("space:view", "picture:view"));

        mockMvc.perform(post("/spaceUser/permissions").param("spaceId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void rejectsAmbiguousResourceParameters() throws Exception {
        mockMvc.perform(post("/spaceUser/permissions")
                        .param("spaceId", "7")
                        .param("pictureId", "8"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void deleteDelegatesToGuardedService() {
        DeleteRequest request = new DeleteRequest();
        request.setId(3L);
        when(spaceUserService.deleteSpaceUser(3L)).thenReturn(true);

        BaseResponse<Boolean> response = controller.deleteSpaceUser(request, httpRequest);

        assertTrue(response.getData());
        verify(spaceUserService).deleteSpaceUser(3L);
    }

    @Test
    void editDelegatesToGuardedService() {
        SpaceUserEditRequest request = new SpaceUserEditRequest();
        request.setId(3L);
        request.setSpaceRole("editor");
        when(spaceUserService.editSpaceUser(request)).thenReturn(true);

        BaseResponse<Boolean> response = controller.editSpaceUser(request, httpRequest);

        assertTrue(response.getData());
        verify(spaceUserService).editSpaceUser(request);
    }
}
