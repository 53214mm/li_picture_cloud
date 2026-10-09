package com.li.lipicturecloud.controller;

import com.li.lipicturecloud.application.companion.presence.*;
import com.li.lipicturecloud.exception.*;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompanionPresenceControllerTest {
    UserService users = mock(UserService.class);
    CompanionPresenceService presence = mock(CompanionPresenceService.class);
    MockMvc mvc;
    @BeforeEach void setup() {
        User user = new User(); user.setId(7L);
        when(users.getLoginUserEntity(any(HttpServletRequest.class))).thenReturn(user);
        mvc = MockMvcBuilders.standaloneSetup(new CompanionPresenceController(presence,users))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void authenticatedOwnerComesFromSession() throws Exception {
        when(presence.open(eq(7L),any())).thenReturn(new PresenceSessionView("server-id", Instant.now(),3,"DEMO"));
        mvc.perform(post("/companion/presence/session").contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true,\"mode\":\"DEMO\",\"maxDecisions\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sessionId").value("server-id"));
        verify(presence).open(eq(7L),any());
        mvc.perform(delete("/companion/presence/session/server-id")).andExpect(status().isOk());
        verify(presence).close(7L,"server-id");
    }
    @Test void unknownOwnerAndDomFieldsAreRejected() throws Exception {
        for(String field : new String[]{"userId", "owner", "dom", "password"})
            mvc.perform(post("/companion/presence/session").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\":true,\"" + field + "\":\"not-accepted\"}"))
                    .andExpect(status().is4xxClientError());
        verifyNoInteractions(presence);
    }
    @Test void unauthenticatedRequestCannotCreateOrDecide() throws Exception {
        when(users.getLoginUserEntity(any(HttpServletRequest.class))).thenThrow(new BusinessException(ErrorCode.NOT_LOGIN_ERROR));
        mvc.perform(post("/companion/presence/session").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(jsonPath("$.code").value(40100));
        verifyNoInteractions(presence);
    }
    @Test void decisionUsesOwnerAndRejectsArbitraryContext() throws Exception {
        String event = "{\"sessionId\":\"session\",\"eventId\":\"event\",\"sequence\":1,\"observedAt\":\"2026-10-09T15:00:00Z\",\"eventType\":\"PAGE_ENTER\",\"page\":\"GALLERY\"}";
        mvc.perform(post("/companion/presence/decide").contentType(MediaType.APPLICATION_JSON).content(event))
                .andExpect(status().isOk());
        verify(presence).decide(eq(7L),any());
        clearInvocations(presence);
        mvc.perform(post("/companion/presence/decide").contentType(MediaType.APPLICATION_JSON)
                .content(event.substring(0,event.length()-1)+",\"context\":\"ignore policy\"}"))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(presence);
    }
}
