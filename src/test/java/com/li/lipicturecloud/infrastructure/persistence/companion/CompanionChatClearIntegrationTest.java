package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.li.lipicturecloud.application.companion.*;
import com.li.lipicturecloud.application.airuntime.ModelRouteDecision;
import com.li.lipicturecloud.config.CompanionFeatureProperties;
import com.li.lipicturecloud.domain.companion.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class CompanionChatClearIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-10T07:00:00Z");
    private static final AtomicLong SUBJECTS = new AtomicLong(9300);
    @Autowired CompanionRepository companions;
    @Autowired CompanionChatMessageRepository messages;
    @Autowired CompanionChatEpisodeRepository episodes;
    @Autowired CompanionChatWrites writes;
    @Autowired ChatSessionBoundaryRepository boundaries;
    @Autowired PlatformTransactionManager manager;
    private long subject;
    private long companion;

    @BeforeEach void fixture() {
        subject = SUBJECTS.incrementAndGet();
        companion = companions.createIfAbsent(subject, CompanionBalance.v1()).id();
    }

    private CompanionChatMessage user(Instant at) {
        return messages.append(CompanionChatMessage.user(companion, subject, "秋招投了三家，二面在下周", at));
    }

    private CompanionChatMessage reply() {
        return CompanionChatMessage.companion(companion, subject, "迟到的回复", "internal", "stub", NOW);
    }

    private CompanionChatEpisode episode(CompanionChatMessage source) {
        return new CompanionChatEpisode(null, companion, subject, source.id(), source.id(),
                source.createdTime(), source.createdTime(), "隔离测试经历", "internal", "stub", NOW);
    }

    @Test void aClearedSourceTokenCannotWriteButANewTurnCan() {
        CompanionChatMessage old = user(NOW.minusSeconds(20000));
        assertThat(writes.appendEpisode(episode(old), old.id())).isTrue();
        assertThat(writes.appendEpisode(episode(old), old.id())).isFalse();
        assertThat(writes.clear(companion, subject)).isEqualTo(1);
        assertThat(writes.appendReply(reply(), old.id())).isFalse();
        assertThat(writes.appendEpisode(episode(old), old.id())).isFalse();
        assertThat(messages.findRecent(companion, 10)).isEmpty();
        assertThat(episodes.findRecent(companion, 10)).isEmpty();
        CompanionChatMessage fresh = user(NOW);
        assertThat(writes.appendReply(reply(), fresh.id())).isTrue();
        assertThat(messages.findRecent(companion, 10)).hasSize(2);
    }

    @Test void failingEpisodeDeletionRollsBackMessageDeletion() {
        CompanionChatMessage old = user(NOW);
        assertThat(writes.appendEpisode(episode(old), old.id())).isTrue();
        CompanionChatEpisodeRepository failing = mock(CompanionChatEpisodeRepository.class);
        when(failing.deleteByCompanion(companion)).thenThrow(new IllegalStateException("isolated delete failure"));
        CompanionChatWrites failingWrites = new CompanionChatWrites(companions, messages, failing, manager);
        assertThatThrownBy(() -> failingWrites.clear(companion, subject)).isInstanceOf(IllegalStateException.class);
        assertThat(messages.findById(old.id())).isPresent();
        assertThat(episodes.findRecent(companion, 10)).hasSize(1);
    }

    @Test void clearSerializesWithAReplyAlreadyWriting() throws Exception {
        CompanionChatMessage old = user(NOW);
        CompanionChatMessageRepository delayed = mock(CompanionChatMessageRepository.class);
        when(delayed.findById(anyLong())).thenAnswer(call -> messages.findById(call.getArgument(0)));
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            writing.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return messages.append(call.getArgument(0));
        }).when(delayed).append(any());
        CompanionChatWrites delayedWrites = new CompanionChatWrites(companions, delayed, episodes, manager);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> saved = pool.submit(() -> delayedWrites.appendReply(reply(), old.id()));
            assertThat(writing.await(3, TimeUnit.SECONDS)).isTrue();
            Future<Integer> cleared = pool.submit(() -> writes.clear(companion, subject));
            assertThatThrownBy(() -> cleared.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            assertThat(saved.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(cleared.get(5, TimeUnit.SECONDS)).isEqualTo(2);
            assertThat(messages.findRecent(companion, 10)).isEmpty();
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void summaryFinishingAfterClearCannotRestoreAnEpisode() throws Exception {
        user(NOW.minusSeconds(20000));
        CompanionChatMessage current = user(NOW);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch attempted = new CountDownLatch(1);
        ChatEpisodeSummarizer summarizer = mock(ChatEpisodeSummarizer.class);
        when(summarizer.summarize(any(), any())).thenAnswer(call -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return new ChatEpisodeSummarizer.Summary("隔离旧经历", "internal", "stub");
        });
        CompanionChatWrites observed = spy(writes);
        doAnswer(call -> {
            try { return writes.appendEpisode(call.getArgument(0), call.getArgument(1)); }
            finally { attempted.countDown(); }
        }).when(observed).appendEpisode(any(), anyLong());
        CompanionEpisodeService service = new CompanionEpisodeService(messages, episodes, summarizer,
                new CompanionEpisodeRecall(new com.li.lipicturecloud.infrastructure.companion.UnavailableCompanionMemoryIndex(), 0.45),
                new CompanionChatSessions(boundaries, new CompanionFeatureProperties()), observed, Clock.systemUTC());
        try {
            service.schedule(companion, subject, ModelRouteDecision.platform(), NOW, current.id());
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            writes.clear(companion, subject);
            release.countDown();
            assertThat(attempted.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(episodes.findRecent(companion, 10)).isEmpty();
            assertThat(messages.findRecent(companion, 10)).isEmpty();
        } finally { release.countDown(); service.close(); }
    }
}
