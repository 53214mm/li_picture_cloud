package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.ChatSessionBoundaryRepository;
import com.li.lipicturecloud.service.event.UserLoggedInEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 记下登录时间：登录之后伙伴聊天从新的一段开始。记录失败不影响登录。
 */
@Component
public class ChatSessionLoginListener {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionLoginListener.class);

    private final ChatSessionBoundaryRepository boundaries;

    public ChatSessionLoginListener(ChatSessionBoundaryRepository boundaries) {
        this.boundaries = boundaries;
    }

    @EventListener
    public void onLogin(UserLoggedInEvent event) {
        try {
            boundaries.recordLogin(event.userId(), event.loginTime());
        } catch (RuntimeException failure) {
            log.warn("companion_chat_boundary_record_failed subjectId={} exceptionType={}",
                    event.userId(), failure.getClass().getName());
        }
    }
}
