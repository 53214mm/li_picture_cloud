package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.ChatSessionBoundaryRepository;
import com.li.lipicturecloud.service.event.UserLoggedInEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChatSessionLoginListenerTest {

    private static final Instant AT = Instant.parse("2026-10-10T07:00:00Z");

    @Test
    void recordsTheLoginTimeAndNeverBreaksLogin() {
        ChatSessionBoundaryRepository boundaries = mock(ChatSessionBoundaryRepository.class);
        ChatSessionLoginListener listener = new ChatSessionLoginListener(boundaries);

        listener.onLogin(new UserLoggedInEvent(7L, AT));
        verify(boundaries).recordLogin(7L, AT);

        doThrow(new IllegalStateException("db down")).when(boundaries).recordLogin(anyLong(), any());
        assertThatCode(() -> listener.onLogin(new UserLoggedInEvent(7L, AT))).doesNotThrowAnyException();
    }
}
