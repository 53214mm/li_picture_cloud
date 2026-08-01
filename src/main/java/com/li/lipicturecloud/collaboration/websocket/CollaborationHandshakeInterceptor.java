package com.li.lipicturecloud.collaboration.websocket;

import com.li.lipicturecloud.constant.UserConstant;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant;
import com.li.lipicturecloud.model.vo.UserVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class CollaborationHandshakeInterceptor implements HandshakeInterceptor {

    public static final String PICTURE_ID = "collaborationPictureId";
    public static final String USER_ID = "collaborationUserId";

    private final SpaceAuthorizationAccessService accessService;

    public CollaborationHandshakeInterceptor(SpaceAuthorizationAccessService accessService) {
        this.accessService = accessService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        HttpSession session = servletRequest.getServletRequest().getSession(false);
        Object loginUser = session == null ? null : session.getAttribute(UserConstant.SESSION_USER_KEY);
        if (!(loginUser instanceof UserVO user)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        String rawPictureId = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("pictureId");
        try {
            Long pictureId = Long.valueOf(rawPictureId);
            accessService.checkForUser(
                    SpaceUserPermissionConstant.COLLABORATION_EDIT, pictureId, user.getId());
            attributes.put(PICTURE_ID, pictureId);
            attributes.put(USER_ID, user.getId());
            return true;
        } catch (RuntimeException exception) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // No temporary resource is allocated during handshake.
    }
}
