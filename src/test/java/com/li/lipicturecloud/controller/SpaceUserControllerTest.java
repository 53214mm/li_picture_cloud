package com.li.lipicturecloud.controller;

import com.li.lipicturecloud.exception.GlobalExceptionHandler;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpaceUserControllerTest {

    private MockMvc mockMvc;
    private SpaceAuthorizationAccessService accessService;

    @BeforeEach
    void setUp() {
        accessService = mock(SpaceAuthorizationAccessService.class);
        SpaceUserController controller = new SpaceUserController();
        ReflectionTestUtils.setField(controller, "authorizationAccessService", accessService);
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
}
