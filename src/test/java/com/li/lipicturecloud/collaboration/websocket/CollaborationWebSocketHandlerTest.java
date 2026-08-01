package com.li.lipicturecloud.collaboration.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.collaboration.CollaborationSessionService;
import com.li.lipicturecloud.collaboration.event.CollaborationEventPublisher;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollaborationWebSocketHandlerTest {

    @Test
    void viewerCommandIsRejectedWithoutChangingCollaborationState() throws IOException {
        CollaborationSessionService sessionService = mock(CollaborationSessionService.class);
        SpaceAuthorizationAccessService accessService = mock(SpaceAuthorizationAccessService.class);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(Map.of(
                CollaborationHandshakeInterceptor.PICTURE_ID, 7L,
                CollaborationHandshakeInterceptor.USER_ID, 8L));
        when(session.isOpen()).thenReturn(true);
        doThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR, "缺少权限：collaboration:edit"))
                .when(accessService).checkForUser(
                        SpaceUserPermissionConstant.COLLABORATION_EDIT, 7L, 8L);

        TestableHandler handler = new TestableHandler(
                sessionService, accessService, new ObjectMapper(), mock(CollaborationEventPublisher.class));
        handler.receive(session, new TextMessage(
                "{\"commandId\":\"cmd-1\",\"operation\":\"ZOOM_IN\",\"baseVersion\":0}"));

        verify(sessionService, never()).apply(org.mockito.ArgumentMatchers.any());
        ArgumentCaptor<TextMessage> response = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(response.capture());
        assertThat(response.getValue().getPayload()).contains("协同操作被拒绝", "collaboration:edit");
    }

    private static class TestableHandler extends CollaborationWebSocketHandler {

        TestableHandler(CollaborationSessionService sessionService,
                        SpaceAuthorizationAccessService accessService,
                        ObjectMapper objectMapper,
                        CollaborationEventPublisher eventPublisher) {
            super(sessionService, accessService, objectMapper, eventPublisher);
        }

        void receive(WebSocketSession session, TextMessage message) throws IOException {
            handleTextMessage(session, message);
        }
    }
}
