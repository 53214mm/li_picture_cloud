package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.CompanionMemory;
import com.li.lipicturecloud.domain.companion.MemorySourceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RelevantCompanionMemoriesTest {
    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");
    private static CompanionMemory memory(long owner, long picture, String text) {
        return CompanionMemory.candidate(11L, owner, picture, picture, MemorySourceType.VISUAL,
                text, BigDecimal.ONE, NOW).confirm(NOW);
    }

    @Test
    void greetingAndGenericMemoryQuestionDoNotInsertRecentPictures() {
        List<CompanionMemory> input = List.of(memory(7, 101, "画面可见一只猫与一杯咖啡。"));
        for (String query : List.of("你好", "你记得什么", "今天工作好累", "今天花了好多钱", "hello", "tell me about a photo")) {
            assertThat(RelevantCompanionMemories.select(query, input, 11, 7, 5)).isEmpty();
        }
    }

    @Test
    void selectsExactObjectQuotedTopicAndPictureIdButDoesNotInventSemanticLinks() {
        CompanionMemory cat = memory(7, 101, "画面可见一只猫在窗台休息。");
        CompanionMemory trip = memory(7, 102, "用户说这次地点是鹿特丹，画面可见建筑。");
        CompanionMemory english = memory(7, 103, "Visible bicycle standing near a wall.");
        List<CompanionMemory> input = List.of(trip, cat, english);
        assertThat(RelevantCompanionMemories.select("那只猫", input, 11, 7, 5)).containsExactly(cat);
        assertThat(RelevantCompanionMemories.select("聊聊“鹿特丹”", input, 11, 7, 5)).containsExactly(trip);
        assertThat(RelevantCompanionMemories.select("图片 #103", input, 11, 7, 5)).containsExactly(english);
        assertThat(RelevantCompanionMemories.select("bicycle", input, 11, 7, 5)).containsExactly(english);
        assertThat(RelevantCompanionMemories.select("宠物", input, 11, 7, 5)).isEmpty();
    }

    @Test
    void recallsExplicitVisualEntityPhrasesInUnquotedNaturalQuestions() {
        CompanionMemory unrelated = memory(7, 101, "画面可见一只猫在窗台休息。");
        CompanionMemory maid = memory(7, 102, "用户修订：这是机械女仆角色的设定图。");
        CompanionMemory twinTails = memory(7, 103, "画面中的角色留着粉色双马尾，背景为浅灰色。");
        List<CompanionMemory> input = List.of(unrelated, twinTails, maid);
        assertThat(RelevantCompanionMemories.select("你还记得那张机械女仆吗", input, 11, 7, 5))
                .containsExactly(maid);
        assertThat(RelevantCompanionMemories.select("那张粉色双马尾的图呢", input, 11, 7, 5))
                .containsExactly(twinTails);
        for (String query : List.of("你好", "那张图片呢", "今天在用机械键盘", "粉色裙子", "双马尾", "女仆")) {
            assertThat(RelevantCompanionMemories.select(query, input, 11, 7, 5)).isEmpty();
        }
    }

    @Test
    void visualEntityPhrasesStillRequireCurrentConfirmedAuthorizedOwnership() {
        CompanionMemory maid = memory(7, 101, "画面包含机械女仆角色。");
        List<CompanionMemory> input = List.of(
                memory(8, 102, "画面包含机械女仆角色。"),
                maid.invalidate("SOURCE_REVOKED", NOW),
                maid.correct("用户修订：这是普通机器人。", NOW),
                CompanionMemory.candidate(11, 7, 103L, 103, MemorySourceType.VISUAL,
                        "画面包含机械女仆角色。", BigDecimal.ONE, NOW),
                CompanionMemory.candidate(12, 7, 104L, 104, MemorySourceType.VISUAL,
                        "画面包含机械女仆角色。", BigDecimal.ONE, NOW).confirm(NOW));
        assertThat(RelevantCompanionMemories.select("你还记得那张机械女仆吗", input, 11, 7, 5)).isEmpty();
    }

    @Test
    void excludesForeignPendingInvalidatedAndOldCorrectedContentAndHonorsZeroLimit() {
        CompanionMemory cat = memory(7, 101, "画面可见一只猫在窗台休息。");
        List<CompanionMemory> input = List.of(memory(8, 102, "画面可见一只猫。"),
                CompanionMemory.candidate(11, 7, 103L, 103, MemorySourceType.VISUAL,
                        "画面可见一只猫。", BigDecimal.ONE, NOW),
                cat.correct("用户修订：这是一只狗。", NOW),
                cat.invalidate("SOURCE_REVOKED", NOW));
        assertThat(RelevantCompanionMemories.select("猫", input, 11, 7, 5)).isEmpty();
        assertThat(RelevantCompanionMemories.select("猫", List.of(cat), 11, 7, 0)).isEmpty();
    }

    @Test
    void latinTokensDoNotMatchSubstrings() {
        assertThat(RelevantCompanionMemories.select("cat", List.of(memory(7, 101,
                "This category contains buildings.")), 11, 7, 5)).isEmpty();
    }
}
