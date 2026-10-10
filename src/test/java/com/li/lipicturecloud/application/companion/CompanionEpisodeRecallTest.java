package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.CompanionChatEpisode;
import com.li.lipicturecloud.infrastructure.companion.UnavailableCompanionMemoryIndex;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CompanionEpisodeRecallTest {

    private static final Instant NOW = Instant.parse("2026-10-10T07:00:00Z");

    private static CompanionChatEpisode episode(long id, String summary, Duration ago) {
        Instant at = NOW.minus(ago);
        return new CompanionChatEpisode(id, 11L, 7L, id * 10, id * 10 + 1, at, at, summary, "dashscope",
                "qwen3.8-max", at);
    }

    // 新→旧
    private final List<CompanionChatEpisode> recent = List.of(
            episode(3, "对方秋招投了三家，二面在下周，有点紧张。", Duration.ofHours(5)),
            episode(2, "", Duration.ofHours(20)),
            episode(1, "对方最近在玩我的世界，想搭一座城堡。", Duration.ofDays(3)));

    private List<Long> ids(List<CompanionChatEpisode> episodes) {
        return episodes.stream().map(CompanionChatEpisode::id).toList();
    }

    @Test
    void unrelatedSmallTalkRecallsNothing() {
        CompanionEpisodeRecall recall = new CompanionEpisodeRecall(new UnavailableCompanionMemoryIndex(), 0.45);

        assertThat(recall.recall("晚上吃什么好", recent, 11L, 7L, 3)).isEmpty();
        // 只是提到"下午"这个时间词，并不是在问以前聊过的事。
        assertThat(recall.recall("今天下午好困", recent, 11L, 7L, 3)).isEmpty();
    }

    @Test
    void relatedTopicsAndQuestionsAboutThePastBringEpisodesBack() {
        CompanionEpisodeRecall recall = new CompanionEpisodeRecall(new UnavailableCompanionMemoryIndex(), 0.45);

        assertThat(ids(recall.recall("二面要准备什么", recent, 11L, 7L, 3))).containsExactly(3L);
        assertThat(ids(recall.recall("城堡搭好了", recent, 11L, 7L, 3))).containsExactly(1L);
        // 问"上次聊了什么"：给最近两段有内容的经历（空的占位段不算）。
        assertThat(ids(recall.recall("我们上次聊了啥", recent, 11L, 7L, 3))).containsExactly(3L, 1L);
    }

    @Test
    void vectorHitsBelowTheThresholdOrFromOtherUsersAreIgnored() {
        FakeIndex index = new FakeIndex();
        index.hits = List.of(new CompanionMemoryIndex.Hit(1L, 0.62), new CompanionMemoryIndex.Hit(3L, 0.31),
                new CompanionMemoryIndex.Hit(77L, 0.95));
        CompanionEpisodeRecall recall = new CompanionEpisodeRecall(index, 0.45);

        assertThat(ids(recall.recall("方块游戏", recent, 11L, 7L, 3))).containsExactly(1L);
        // 只给有内容的经历建向量。
        assertThat(index.fingerprints).containsOnlyKeys(1L, 3L);
    }

    @Test
    void clearedHistoryRemovesStaleVectors() {
        FakeIndex index = new FakeIndex();
        index.fingerprints.put(3L, "old");
        CompanionEpisodeRecall recall = new CompanionEpisodeRecall(index, 0.45);

        assertThat(recall.recall("你好", List.of(), 11L, 7L, 3)).isEmpty();
        assertThat(index.removed).containsExactly(3L);
    }

    private static final class FakeIndex implements CompanionMemoryIndex {
        final Map<Long, String> fingerprints = new LinkedHashMap<>();
        final List<Long> removed = new ArrayList<>();
        List<Hit> hits = List.of();

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public Map<Long, String> fingerprints(long companionId, long subjectId) {
            return Map.copyOf(fingerprints);
        }

        @Override
        public void upsert(long companionId, long subjectId, List<Entry> entries) {
            entries.forEach(entry -> fingerprints.put(entry.memoryId(), entry.fingerprint()));
        }

        @Override
        public void remove(Collection<Long> memoryIds) {
            removed.addAll(memoryIds);
            memoryIds.forEach(fingerprints::remove);
        }

        @Override
        public List<Hit> search(long companionId, long subjectId, String query, int limit) {
            return hits;
        }
    }
}
