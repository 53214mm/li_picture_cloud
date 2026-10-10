package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.airuntime.ModelRouteDecision;
import com.li.lipicturecloud.config.CompanionFeatureProperties;
import com.li.lipicturecloud.domain.companion.ChatSessionBoundaryRepository;
import com.li.lipicturecloud.domain.companion.CompanionChatEpisode;
import com.li.lipicturecloud.domain.companion.CompanionChatEpisodeRepository;
import com.li.lipicturecloud.domain.companion.CompanionChatMessage;
import com.li.lipicturecloud.domain.companion.CompanionChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanionEpisodeServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T07:00:00Z");
    private final ModelRouteDecision route = ModelRouteDecision.platform();

    private CompanionChatMessageRepository messages;
    private CompanionChatEpisodeRepository episodes;
    private ChatEpisodeSummarizer summarizer;
    private CompanionEpisodeService service;

    @BeforeEach
    void setUp() {
        messages = mock(CompanionChatMessageRepository.class);
        episodes = mock(CompanionChatEpisodeRepository.class);
        summarizer = mock(ChatEpisodeSummarizer.class);
        when(episodes.append(any())).thenReturn(true);
        service = new CompanionEpisodeService(messages, episodes, summarizer,
                new CompanionEpisodeRecall(new com.li.lipicturecloud.infrastructure.companion.UnavailableCompanionMemoryIndex(), 0.45),
                new CompanionChatSessions(mock(ChatSessionBoundaryRepository.class), new CompanionFeatureProperties()),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CompanionChatMessage user(long id, Duration ago, String content) {
        return CompanionChatMessage.user(11L, 7L, content, NOW.minus(ago)).withId(id);
    }

    private static CompanionChatMessage reply(long id, Duration ago) {
        return CompanionChatMessage.companion(11L, 7L, "嗯嗯", "dashscope", "qwen3.8-max", NOW.minus(ago)).withId(id);
    }

    @Test
    void summarisesOnlyTheLatestTwoUnfinishedConversationsAndSkipsSmallTalk() {
        when(episodes.lastCoveredMessageId(11L)).thenReturn(10L);
        // 新→旧：今天上午聊秋招、昨天只说了句"你好"、三天前一段更早的（积压太多时跳过）。
        when(messages.findOlder(11L, 10L, NOW, 120)).thenReturn(List.of(
                reply(26, Duration.ofHours(5)), user(25, Duration.ofHours(5), "秋招投了三家，二面在下周"),
                reply(24, Duration.ofHours(27)), user(23, Duration.ofHours(27), "你好"),
                user(21, Duration.ofDays(3), "三天前说的事情很长很长")));
        when(summarizer.summarize(eq(route), any())).thenReturn(
                new ChatEpisodeSummarizer.Summary("对方秋招投了三家，二面在下周。", "dashscope", "qwen3.8-max"));

        service.consolidate(11L, 7L, route, NOW, 99L);

        ArgumentCaptor<CompanionChatEpisode> stored = ArgumentCaptor.forClass(CompanionChatEpisode.class);
        verify(episodes, times(2)).append(stored.capture());
        // 先整理更早的那段，进度只往前推。
        assertThat(stored.getAllValues()).extracting(CompanionChatEpisode::lastMessageId).containsExactly(24L, 26L);
        assertThat(stored.getAllValues().get(0).memorable()).isFalse();
        assertThat(stored.getAllValues().get(1).summary()).isEqualTo("对方秋招投了三家，二面在下周。");
        assertThat(stored.getAllValues().get(1).startedTime()).isEqualTo(NOW.minus(Duration.ofHours(5)));
        verify(summarizer, times(1)).summarize(any(), any());
    }

    @Test
    void aFailedSummaryLeavesTheConversationForTheNextTurn() {
        when(messages.findOlder(anyLong(), anyLong(), any(), anyInt())).thenReturn(List.of(
                user(25, Duration.ofHours(5), "秋招投了三家，二面在下周")));
        when(summarizer.summarize(any(), any())).thenThrow(new IllegalStateException("timeout"));

        service.consolidate(11L, 7L, route, NOW, 99L);

        verify(episodes, never()).append(any());
    }

    @Test
    void theMessageJustSentIsNeverTreatedAsAnOldConversation() {
        when(messages.findOlder(anyLong(), anyLong(), any(), anyInt())).thenReturn(List.of(
                user(99, Duration.ZERO, "这句是刚发的，还在聊")));

        service.consolidate(11L, 7L, route, NOW, 99L);

        verify(summarizer, never()).summarize(any(), any());
        verify(episodes, never()).append(any());
    }

    @Test
    void clearingForgetsEpisodes() {
        when(episodes.deleteByCompanion(11L)).thenReturn(3);
        assertThat(service.clear(11L)).isEqualTo(3);
    }
}
