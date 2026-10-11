package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.CompanionChatMessage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompanionChatSessionsTest {

    private static final Instant NOW = Instant.parse("2026-10-10T07:00:00Z");
    private static final Duration GAP = Duration.ofHours(2);

    private static CompanionChatMessage at(long id, Duration ago) {
        return CompanionChatMessage.user(11L, 7L, "第" + id + "句", NOW.minus(ago)).withId(id);
    }

    @Test
    void keepsTheMessagesOfTheRunningConversationOnly() {
        List<CompanionChatMessage> newestFirst = List.of(at(5, Duration.ofMinutes(3)), at(4, Duration.ofMinutes(40)),
                at(3, Duration.ofHours(5)), at(2, Duration.ofHours(5).plusMinutes(1)));

        CompanionChatSessions.Session session = CompanionChatSessions.cut(newestFirst, null, NOW, GAP);

        assertThat(session.messagesNewestFirst()).extracting(CompanionChatMessage::id).containsExactly(5L, 4L);
        assertThat(session.continuing()).isTrue();
        assertThat(session.start()).isEqualTo(NOW.minus(Duration.ofMinutes(40)));
        assertThat(session.previousMessageAt()).isEqualTo(NOW.minus(Duration.ofMinutes(3)));
    }

    @Test
    void aLongPauseOrALoginStartsANewConversation() {
        List<CompanionChatMessage> morning = List.of(at(3, Duration.ofHours(5)), at(2, Duration.ofHours(5).plusMinutes(2)));
        CompanionChatSessions.Session afterPause = CompanionChatSessions.cut(morning, null, NOW, GAP);
        assertThat(afterPause.continuing()).isFalse();
        assertThat(afterPause.start()).isEqualTo(NOW);
        assertThat(afterPause.previousMessageAt()).isEqualTo(NOW.minus(Duration.ofHours(5)));

        List<CompanionChatMessage> recent = List.of(at(5, Duration.ofMinutes(5)), at(4, Duration.ofMinutes(20)));
        CompanionChatSessions.Session afterLogin = CompanionChatSessions.cut(recent, NOW.minus(Duration.ofMinutes(10)),
                NOW, GAP);
        assertThat(afterLogin.messagesNewestFirst()).extracting(CompanionChatMessage::id).containsExactly(5L);
    }

    @Test
    void splitsOldConversationsByTheSamePause() {
        List<CompanionChatMessage> oldestFirst = List.of(at(1, Duration.ofHours(30)), at(2, Duration.ofHours(29)),
                at(3, Duration.ofHours(6)), at(4, Duration.ofHours(5)));

        assertThat(CompanionChatSessions.split(oldestFirst, GAP))
                .extracting(group -> group.stream().map(CompanionChatMessage::id).toList())
                .containsExactly(List.of(1L, 2L), List.of(3L, 4L));
        assertThat(CompanionChatSessions.split(List.of(), GAP)).isEmpty();
    }
}
