package com.li.lipicturecloud.collaboration.websocket;

import com.li.lipicturecloud.constant.UserConstant;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant;
import com.li.lipicturecloud.model.vo.UserVO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CollaborationHandshakeInterceptorTest {

    @Test
    void acceptsLoggedInEditorAndStoresTrustedAttributes() {
        SpaceAuthorizationAccessService access = mock(SpaceAuthorizationAccessService.class);
        CollaborationHandshakeInterceptor interceptor = new CollaborationHandshakeInterceptor(access);
        MockHttpServletRequest servletRequest = requestWithUser(8L);
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest), mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class), attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry(CollaborationHandshakeInterceptor.PICTURE_ID, 7L)
                .containsEntry(CollaborationHandshakeInterceptor.USER_ID, 8L);
        verify(access).checkForUser(SpaceUserPermissionConstant.COLLABORATION_JOIN, 7L, 8L);
    }

    @Test
    void rejectsAnonymousHandshake() {
        CollaborationHandshakeInterceptor interceptor = new CollaborationHandshakeInterceptor(
                mock(SpaceAuthorizationAccessService.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ws/collaboration");
        request.setQueryString("pictureId=7");
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean accepted = interceptor.beforeHandshake(new ServletServerHttpRequest(request), response,
                mock(WebSocketHandler.class), new HashMap<>());

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    private MockHttpServletRequest requestWithUser(long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ws/collaboration");
        request.setQueryString("pictureId=7");
        UserVO user = new UserVO();
        user.setId(userId);
        request.getSession().setAttribute(UserConstant.SESSION_USER_KEY, user);
        return request;
    }
}
