package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.CompanionMemory;
import com.li.lipicturecloud.domain.companion.MemorySourceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CompanionMemoryRecallTest {

    private static final Instant NOW = Instant.parse("2026-10-10T03:00:00Z");
    private static final long COMPANION = 11L;
    private static final long SUBJECT = 7L;

    private final CompanionMemory knight = memory(1L, 301L, "可见观察：一位少女身穿银色盔甲，双手举剑站在城墙前。");
    private final CompanionMemory minecraft = memory(2L, 302L,
            "可见观察：画面中央是方块风格的游戏图标，被红色叉号覆盖。 可能的角色或出处（推测）：可能是《我的世界》相关的梗图");
    private final CompanionMemory maid = memory(3L, 303L, "可见观察：一位机械女仆站在书架旁，阳光透过窗帘洒在地板上。");
    private final Map<Long, PictureLabel> labels = Map.of(
            301L, new PictureLabel(301L, "骑士", "插画", List.of("少女", "剑")),
            302L, new PictureLabel(302L, "你可以玩我的世界", "", List.of()));

    private static CompanionMemory memory(long id, long pictureId, String content) {
        return CompanionMemory.restore(id, COMPANION, SUBJECT, pictureId, 20L + id, MemorySourceType.VISUAL,
                content, content, new BigDecimal("0.9"),
                com.li.lipicturecloud.domain.companion.MemoryStatus.CONFIRMED, null, 1L, NOW, NOW);
    }

    private CompanionMemoryRecall lexicalRecall() {
        return new CompanionMemoryRecall(new com.li.lipicturecloud.infrastructure.companion.UnavailableCompanionMemoryIndex(),
                ids -> labels, 0.45);
    }

    private List<Long> recalledIds(CompanionMemoryRecall.MemoryRecall recall) {
        return recall.recalled().stream().map(recalled -> recalled.memory().id()).toList();
    }

    @Test
    void lexicalFallbackMatchesPictureNamesTagsAndContentButNotSmallTalk() {
        List<CompanionMemory> usable = List.of(maid, minecraft, knight);
        CompanionMemoryRecall recall = lexicalRecall();

        assertThat(recalledIds(recall.recall("还记得那张骑士吗", usable, COMPANION, SUBJECT, 5))).containsExactly(1L);
        assertThat(recalledIds(recall.recall("少女举剑那张", usable, COMPANION, SUBJECT, 5))).containsExactly(1L);
        assertThat(recalledIds(recall.recall("emmm，我的世界？", usable, COMPANION, SUBJECT, 5))).containsExactly(2L);
        assertThat(recalledIds(recall.recall("机械女仆的图的记忆你有吗", usable, COMPANION, SUBJECT, 5))).containsExactly(3L);
        assertThat(recall.recall("今天过得怎么样", usable, COMPANION, SUBJECT, 5).recalled()).isEmpty();
    }

    @Test
    void sheKnowsHowManyPicturesSheRemembersAndListsThemOnlyWhenAsked() {
        List<CompanionMemory> usable = List.of(maid, minecraft, knight);
        CompanionMemoryRecall recall = lexicalRecall();

        CompanionMemoryRecall.MemoryRecall casual = recall.recall("你好", usable, COMPANION, SUBJECT, 5);
        assertThat(casual.pictureCount()).isEqualTo(3);
        assertThat(casual.recentPictures()).isEmpty();

        CompanionMemoryRecall.MemoryRecall asked = recall.recall("我喂你的图片你能记住吗", usable, COMPANION, SUBJECT, 5);
        assertThat(asked.recentPictures()).extracting(recalled -> recalled.memory().id()).containsExactly(3L, 2L, 1L);
        assertThat(asked.recentPictures().get(2).label().name()).isEqualTo("骑士");

        CompanionMemoryRecall.MemoryRecall named = recall.recall("还记得图片 303 里的机械女仆吗", usable, COMPANION, SUBJECT, 5);
        assertThat(named.recalled()).extracting(recalled -> recalled.memory().id()).first().isEqualTo(3L);
        assertThat(named.recentPictures()).isEmpty();
    }

    @Test
    void explicitPictureIdsAreHonouredAndForeignMemoriesAreIgnored() {
        CompanionMemory foreign = CompanionMemory.restore(9L, COMPANION, 8L, 309L, 29L, MemorySourceType.VISUAL,
                "别人的骑士图", "别人的骑士图", new BigDecimal("0.9"),
                com.li.lipicturecloud.domain.companion.MemoryStatus.CONFIRMED, null, 1L, NOW, NOW);
        CompanionMemoryRecall.MemoryRecall recall = lexicalRecall()
                .recall("图片 #303 和骑士", List.of(maid, knight, foreign), COMPANION, SUBJECT, 5);

        assertThat(recalledIds(recall)).containsExactly(3L, 1L);
    }

    @Test
    void vectorSearchUsesSimilarityThresholdAndOnlyUsableMemories() {
        FakeIndex index = new FakeIndex();
        index.hits = List.of(new CompanionMemoryIndex.Hit(2L, 0.71), new CompanionMemoryIndex.Hit(99L, 0.9),
                new CompanionMemoryIndex.Hit(1L, 0.30));
        CompanionMemoryRecall recall = new CompanionMemoryRecall(index, ids -> labels, 0.45);

        CompanionMemoryRecall.MemoryRecall result = recall.recall("聊聊那个方块游戏", List.of(knight, minecraft),
                COMPANION, SUBJECT, 5);

        // 99 不是可用记忆（已删除或撤权），1 相似度不够。
        assertThat(recalledIds(result)).containsExactly(2L);
        // 首次使用时把两条记忆补进索引，索引文本包含图片名和标签。
        assertThat(index.upserted).extracting(CompanionMemoryIndex.Entry::memoryId).containsExactlyInAnyOrder(1L, 2L);
        assertThat(index.upserted.stream().filter(entry -> entry.memoryId() == 1L).findFirst().orElseThrow().text())
                .startsWith("《骑士》 分类：插画 标签：少女、剑");
    }

    @Test
    void vectorReconcileSkipsUnchangedMemoriesAndRemovesStaleVectors() {
        FakeIndex index = new FakeIndex();
        CompanionMemoryRecall recall = new CompanionMemoryRecall(index, ids -> labels, 0.45);
        recall.recall("你好", List.of(knight), COMPANION, SUBJECT, 5);
        index.fingerprints.put(77L, "gone");
        index.upserted.clear();

        recall.recall("你好", List.of(knight), COMPANION, SUBJECT, 5);

        assertThat(index.upserted).isEmpty();
        assertThat(index.removed).containsExactly(77L);
    }

    @Test
    void anIndexFailureFallsBackToLexicalMatching() {
        FakeIndex index = new FakeIndex();
        index.fail = true;
        CompanionMemoryRecall recall = new CompanionMemoryRecall(index, ids -> labels, 0.45);

        assertThat(recalledIds(recall.recall("骑士", List.of(knight, maid), COMPANION, SUBJECT, 5))).containsExactly(1L);
    }

    @Test
    void aHealthyPartialIndexStillRecallsNamesAndTagsOutsideItsFirstBatch() {
        FakeIndex index = new FakeIndex();
        List<CompanionMemory> usable = new ArrayList<>();
        for (int i = 10; i < 35; i++) {
            usable.add(memory(i, 400L + i, "普通风景"));
        }
        usable.add(knight);
        usable.addFirst(maid);
        CompanionMemoryRecall recall = new CompanionMemoryRecall(index, ids -> labels, 0.45);
        assertThat(recalledIds(recall.recall("机械女仆和骑士少女", usable, COMPANION, SUBJECT, 5)))
                .containsExactlyInAnyOrder(1L, 3L);
        assertThat(index.upserted).hasSize(20);
        assertThat(recalledIds(recall.recall("图片 #301", usable, COMPANION, SUBJECT, 5))).first().isEqualTo(1L);
    }

    @Test
    void aCompleteHealthyIndexWithoutSemanticHitsStillRecallsExactWords() {
        FakeIndex index = new FakeIndex();
        CompanionMemoryRecall recall = new CompanionMemoryRecall(index, ids -> labels, 0.45);
        recall.recall("你好", List.of(knight, maid), COMPANION, SUBJECT, 5);
        assertThat(recalledIds(recall.recall("骑士", List.of(knight, maid), COMPANION, SUBJECT, 5)))
                .containsExactly(1L);
    }

    @Test
    void noUsableMemoriesMeansNothingToRecall() {
        CompanionMemoryRecall.MemoryRecall recall = lexicalRecall().recall("骑士", List.of(), COMPANION, SUBJECT, 5);
        assertThat(recall.recalled()).isEmpty();
        assertThat(recall.pictureCount()).isZero();
    }

    @Test
    void followupsResolveOnlyTheLatestUserPictureTopic() {
        var recall = lexicalRecall();
        var usable = List.of(maid, knight);
        for (String query : List.of("现在呢", "刚喂了", "那张图有什么内容")) {
            assertThat(recalledIds(recall.recall(query, usable, COMPANION, SUBJECT, 5,
                    List.of("图片301的内容")))).containsExactly(1L);
        }
        assertThat(recalledIds(recall.recall("图片303", usable, COMPANION, SUBJECT, 5,
                List.of("图片301的内容")))).containsExactly(3L);
        assertThat(recall.recall("现在呢", usable, COMPANION, SUBJECT, 5,
                List.of("晚饭吃什么", "图片301的内容")).recalled()).isEmpty();
        assertThat(recall.recall("你好", usable, COMPANION, SUBJECT, 5,
                List.of("图片301的内容")).recalled()).isEmpty();
        assertThat(recall.recall("现在呢", usable, COMPANION, SUBJECT, 5).recalled()).isEmpty();
        for (String smallTalk : List.of("今晚去图书馆吗", "我的记忆力最近很差")) {
            assertThat(recall.recall("现在呢", List.of(knight), COMPANION, SUBJECT, 5,
                    List.of(smallTalk)).recalled()).isEmpty();
        }
    }

    @Test
    void ambiguousReferencesDoNotPickAnArbitraryPictureAndLimitsApplyToCandidates() {
        var result = lexicalRecall().recall("那张图", List.of(maid, knight), COMPANION, SUBJECT, 5,
                List.of("我喂过图片301和图片303"));
        assertThat(result.referenceAmbiguous()).isTrue();
        assertThat(result.recalled()).isEmpty();
        assertThat(result.recentPictures()).hasSize(2);
        var lexicalAmbiguity = lexicalRecall().recall("那张图里的女仆和骑士是谁", List.of(maid, knight),
                COMPANION, SUBJECT, 5);
        assertThat(lexicalAmbiguity.referenceAmbiguous()).isTrue();
        assertThat(lexicalAmbiguity.recalled()).isEmpty();
        assertThat(lexicalRecall().recall("我喂过哪些图片", List.of(maid, knight),
                COMPANION, SUBJECT, 0).recentPictures()).isEmpty();
        assertThat(lexicalRecall().recall("那张图", List.of(knight), COMPANION, SUBJECT, 5,
                List.of("图片999的内容")).recalled()).isEmpty();
        assertThat(lexicalRecall().recall("现在呢", List.of(knight), COMPANION, SUBJECT, 5,
                List.of("图片999里的骑士")).recalled()).isEmpty();
    }

    @Test
    void oneConfirmedPictureCanAnswerAnExplicitPictureFollowupWithoutHistory() {
        assertThat(recalledIds(lexicalRecall().recall("那张图有什么内容", List.of(knight),
                COMPANION, SUBJECT, 5))).containsExactly(1L);
        var pending = CompanionMemory.candidate(COMPANION, SUBJECT, 302L, 21L, MemorySourceType.VISUAL,
                "待确认内容", new BigDecimal("0.8"), NOW).withId(8L);
        assertThat(recalledIds(lexicalRecall().recall("刚喂了", List.of(pending, knight),
                COMPANION, SUBJECT, 5, List.of("图片302")))).isEmpty();
    }

    @Test
    void consecutiveShortFollowupsRemainBoundedAndDoNotCrossANewUserTopic() {
        var recall = lexicalRecall();
        var usable = List.of(maid, knight);
        assertThat(recalledIds(recall.recall("现在呢", usable, COMPANION, SUBJECT, 5,
                List.of("现在哩", "刚喂了", "图片301的内容")))).containsExactly(1L);
        assertThat(recall.recall("现在呢", usable, COMPANION, SUBJECT, 5,
                List.of("刚喂了", "晚饭吃什么", "图片301的内容")).recalled()).isEmpty();
        assertThat(recall.recall("现在呢", usable, COMPANION, SUBJECT, 5,
                List.of("那张图先不聊了，晚饭吃什么", "图片301的内容")).recalled()).isEmpty();
        var mixedTopic = recall.recall("现在呢", List.of(knight), COMPANION, SUBJECT, 5,
                List.of("那张图先不聊了，晚饭吃什么", "图片301的内容"));
        assertThat(mixedTopic.recalled()).isEmpty();
        assertThat(mixedTopic.recentPictures()).isEmpty();
        assertThat(recall.recall("现在呢", usable, COMPANION, SUBJECT, 5,
                List.of("现在哩", "刚喂了", "现在呢", "图片301的内容")).recalled()).isEmpty();
    }

    private static final class FakeIndex implements CompanionMemoryIndex {
        final Map<Long, String> fingerprints = new LinkedHashMap<>();
        final List<Entry> upserted = new ArrayList<>();
        final List<Long> removed = new ArrayList<>();
        List<Hit> hits = List.of();
        boolean fail;

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public Map<Long, String> fingerprints(long companionId, long subjectId) {
            if (fail) {
                throw new IndexUnavailableException("down", null);
            }
            return Map.copyOf(fingerprints);
        }

        @Override
        public void upsert(long companionId, long subjectId, List<Entry> entries) {
            upserted.addAll(entries);
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
