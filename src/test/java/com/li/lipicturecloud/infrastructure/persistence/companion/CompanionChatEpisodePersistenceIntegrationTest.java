package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.li.lipicturecloud.domain.companion.ChatSessionBoundaryRepository;
import com.li.lipicturecloud.domain.companion.CompanionChatEpisode;
import com.li.lipicturecloud.domain.companion.CompanionChatEpisodeRepository;
import com.li.lipicturecloud.domain.companion.CompanionChatMessage;
import com.li.lipicturecloud.domain.companion.CompanionChatMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CompanionChatEpisodePersistenceIntegrationTest {

    private static final Instant MORNING = Instant.parse("2026-10-10T01:40:00Z");

    @Autowired
    private CompanionChatEpisodeRepository episodes;
    @Autowired
    private CompanionChatMessageRepository messages;
    @Autowired
    private ChatSessionBoundaryRepository boundaries;

    @Test
    void episodesAdvanceTheCursorOnceAndCanBeCleared() {
        assertThat(episodes.lastCoveredMessageId(8801L)).isZero();
        CompanionChatEpisode episode = new CompanionChatEpisode(null, 8801L, 7L, 40L, 41L, MORNING, MORNING,
                "对方秋招二面在下周。", "dashscope", "qwen3.8-max", MORNING);

        assertThat(episodes.append(episode)).isTrue();
        // 同一段不会被并发的另一轮重复整理。
        assertThat(episodes.append(episode)).isFalse();
        assertThat(episodes.append(new CompanionChatEpisode(null, 8801L, 7L, 50L, 52L, MORNING.plusSeconds(9000),
                MORNING.plusSeconds(9100), "", null, null, MORNING))).isTrue();

        assertThat(episodes.lastCoveredMessageId(8801L)).isEqualTo(52L);
        assertThat(episodes.findRecent(8801L, 10)).extracting(CompanionChatEpisode::lastMessageId)
                .containsExactly(52L, 41L);
        assertThat(episodes.deleteByCompanion(8801L)).isEqualTo(2);
        assertThat(episodes.findRecent(8801L, 10)).isEmpty();
    }

    @Test
    void olderMessagesAreReadAfterTheCursorAndBeforeTheSession() {
        CompanionChatMessage first = messages.append(CompanionChatMessage.user(8802L, 7L, "上午一", MORNING));
        CompanionChatMessage second = messages.append(CompanionChatMessage.user(8802L, 7L, "上午二", MORNING.plusSeconds(60)));
        messages.append(CompanionChatMessage.user(8802L, 7L, "下午", MORNING.plusSeconds(6 * 3600)));

        assertThat(messages.findOlder(8802L, 0L, MORNING.plusSeconds(3600), 10))
                .extracting(CompanionChatMessage::content).containsExactly("上午二", "上午一");
        assertThat(messages.findOlder(8802L, first.id(), MORNING.plusSeconds(3600), 10))
                .extracting(CompanionChatMessage::id).containsExactly(second.id());
    }

    @Test
    void theLatestLoginIsKeptPerUser() {
        assertThat(boundaries.lastLogin(8803L)).isEmpty();
        boundaries.recordLogin(8803L, MORNING);
        boundaries.recordLogin(8803L, MORNING.plusSeconds(600));

        assertThat(boundaries.lastLogin(8803L)).contains(MORNING.plusSeconds(600));
    }
}
