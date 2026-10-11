package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.domain.companion.CompanionMemory;
import com.li.lipicturecloud.domain.companion.CompanionMood;
import com.li.lipicturecloud.domain.companion.CompanionRelationship;
import com.li.lipicturecloud.domain.companion.CompanionRelationshipRepository;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
import com.li.lipicturecloud.domain.companion.CompanionTraits;
import com.li.lipicturecloud.domain.companion.MemorySourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanionChatContextAssemblerTest {

    private static final Instant NOW = Instant.parse("2026-08-14T08:00:00Z");

    private CompanionRepository companionRepository;
    private CompanionRelationshipRepository relationshipRepository;
    private CompanionChatContextAssembler assembler;

    @BeforeEach
    void setUp() {
        companionRepository = mock(CompanionRepository.class);
        relationshipRepository = mock(CompanionRelationshipRepository.class);
        // 情绪与记忆由调用方在撤权/衰减检查后注入；组装器只格式化，不自行查询。
        assembler = new CompanionChatContextAssembler(companionRepository, relationshipRepository);
    }

    @Test
    void promptContainsOnlyPersistedFactsAndInjectedUsableMemories() {
        Companion companion = Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
        CompanionMemory confirmed = CompanionMemory.candidate(11L, 7L, 101L, 21L,
                MemorySourceType.VISUAL, "伙伴记得一张明亮的图片。", new BigDecimal("0.8"), NOW)
                .confirm(NOW);
        CompanionMemory pending = CompanionMemory.candidate(11L, 7L, 102L, 22L,
                MemorySourceType.VISUAL, "待确认的记忆不应进入上下文。", new BigDecimal("0.5"), NOW);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(relationshipRepository.findByCompanionAndSubject(11L, 7L))
                .thenReturn(Optional.of(CompanionRelationship.restore(61L, 11L, 7L,
                        bd("40"), bd("20"), bd("10"), bd("5"), bd("3"), 2L)));

        String prompt = assembler.systemPrompt(11L, 7L, 5,
                new CompanionMood(51L, 11L, bd("30"), bd("20"), bd("0"), bd("10"), bd("0"),
                        1L, NOW),
                List.of(pending, confirmed));

        assertThat(prompt).contains("你是绫页", "AI", "不是你的主人", "不要主动提图片", "只是推测");
        // 内部阶段名、"主人"称谓和情绪数值都不再进入提示词。
        assertThat(prompt).doesNotContain("光点", "幼体", "与主人的关系", "精力 30", "让用户愿意继续");
        assertThat(prompt).contains("- 成长：刚诞生不久（你的名字始终是绫页）", "- 心情：平常", "- 和对方：刚认识");
        // 调用方保证可用记忆已通过撤权检查；这里仍防御性只放行 CONFIRMED。
        assertThat(prompt).contains("这轮可能用得上的记录", "[对方上传的图片 #101 · 8月14日 · 视觉模型的观察与推测] 伙伴记得一张明亮的图片。");
        assertThat(prompt).doesNotContain("待确认的记忆不应进入上下文");
        assertThat(prompt).contains("不要模仿那种口吻");
    }

    @Test
    void promptStatesNoMemoriesAndNeutralMoodInsteadOfFabricating() {
        Companion companion = Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));

        String prompt = assembler.systemPrompt(11L, 7L, 5, null, List.of());

        // 没有相关记录时整段不出现，避免空段落把注意力拉回图片。
        assertThat(prompt).doesNotContain("这轮可能用得上的记录");
        assertThat(prompt).contains("编造");
        assertThat(prompt).contains("- 心情：平常", "- 和对方：刚认识");
        assertThat(prompt).doesNotContain("最近的小倾向");
    }

    @Test
    void noConfirmedMemoriesDoesNotClaimNoFeedingEverOccurred() {
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(
                Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L)));
        String prompt = assembler.systemPrompt(11L, 7L, 5, null, List.of());
        assertThat(prompt).contains("没有可用于对话的已确认图片记忆");
        assertThat(prompt).doesNotContain("对方还没有喂过");
    }

    @Test
    void stateIsReducedToToneHintsWithoutNumbers() {
        Companion awakened = Companion.awaken(7L, CompanionBalance.v1());
        Companion companion = Companion.restore(11L, 7L, 0L, 1, awakened.lifeStage(),
                new CompanionTraits(bd("45"), bd("0"), bd("-50"), bd("10"), bd("35")),
                awakened.skillExperience(), awakened.balanceVersion(), 0L, CompanionBalance.v1());
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(relationshipRepository.findByCompanionAndSubject(11L, 7L))
                .thenReturn(Optional.of(CompanionRelationship.restore(61L, 11L, 7L,
                        bd("70"), bd("60"), bd("50"), bd("40"), bd("-5"), 2L)));

        String prompt = assembler.systemPrompt(11L, 7L, 5,
                new CompanionMood(51L, 11L, bd("65"), bd("20"), bd("0"), bd("10"), bd("80"), 1L, NOW),
                List.of());

        // 最强的明显维度决定心情词；倾向只取明显的前两项。
        assertThat(prompt).contains("- 心情：有点烦", "- 和对方：已经挺熟，玩笑可以更放开；最近有点小摩擦",
                "- 最近的小倾向：更好奇一点、更沉稳一点（只影响细节，不改变上面的性格）");
        assertThat(prompt).doesNotContain("更爱创造一点", "65", "80");
    }

    @Test
    void longMemoryContentIsTruncatedForThePrompt() {
        Companion companion = Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
        // 284 码点：在记忆 300 上限内，但超过上下文的 240 截断线。
        String longContent = "伙伴记得".concat("内容".repeat(140));
        CompanionMemory confirmed = CompanionMemory.candidate(11L, 7L, 101L, 21L,
                MemorySourceType.VISUAL, longContent, new BigDecimal("0.8"), NOW).confirm(NOW);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));

        String prompt = assembler.systemPrompt(11L, 7L, 5, null, List.of(confirmed));

        // 截断标记存在，且完整原文（204 码点）不会整段出现在提示词中。
        assertThat(prompt).contains("……");
        assertThat(prompt).doesNotContain(longContent);
    }

    @Test
    void usableMemoriesAreCappedByTheMemoryLimit() {
        Companion companion = Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
        List<CompanionMemory> memories = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> CompanionMemory.candidate(11L, 7L, 101L + index, 31L + index,
                        MemorySourceType.VISUAL, "伙伴记得第" + (index + 1) + "张图片。",
                        new BigDecimal("0.8"), NOW).confirm(NOW))
                .toList();
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));

        String prompt = assembler.systemPrompt(11L, 7L, 5, null, memories);

        assertThat(prompt).contains("伙伴记得第1张图片。");
        assertThat(prompt).contains("伙伴记得第5张图片。");
        assertThat(prompt).doesNotContain("伙伴记得第6张图片。");
    }

    @Test
    void rejectsWrongOwnerAndCompanionEvenInInjectedInputAndAllowsZeroBudget() {
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(
                Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L)));
        CompanionMemory foreign = CompanionMemory.candidate(11L, 8L, 101L, 21L,
                MemorySourceType.VISUAL, "不应展示的其他用户图片。", BigDecimal.ONE, NOW).confirm(NOW);
        CompanionMemory otherCompanion = CompanionMemory.candidate(12L, 7L, 102L, 22L,
                MemorySourceType.VISUAL, "不应展示的其他伙伴图片。", BigDecimal.ONE, NOW).confirm(NOW);
        CompanionMemory owned = CompanionMemory.candidate(11L, 7L, 103L, 23L,
                MemorySourceType.VISUAL, "本人的可用图片。", BigDecimal.ONE, NOW).confirm(NOW);
        assertThat(assembler.systemPrompt(11L, 7L, 5, null, List.of(foreign, otherCompanion, owned)))
                .contains("本人的可用图片。").doesNotContain("不应展示");
        assertThat(assembler.systemPrompt(11L, 7L, 0, null, List.of(owned)))
                .doesNotContain("本人的可用图片。");
    }

    @Test
    void recallShowsMemoryAwarenessLabelsAndTheRecentListOnlyWhenGiven() {
        Companion companion = Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        CompanionMemory knight = CompanionMemory.candidate(11L, 7L, 301L, 21L, MemorySourceType.VISUAL,
                "可见观察：一位少女身穿银色盔甲，双手举剑。", new BigDecimal("0.8"), NOW).confirm(NOW);
        PictureLabel label = new PictureLabel(301L, "骑士", "", List.of("剑"));
        CompanionMemoryRecall.RecalledMemory recalled = new CompanionMemoryRecall.RecalledMemory(knight, label);

        String quiet = assembler.systemPrompt(11L, 7L, 5, null,
                new CompanionMemoryRecall.MemoryRecall(List.of(), List.of(), 3));
        assertThat(quiet).contains("- 记忆：你记得对方喂过的 3 张图；对方问起或聊到相关的才用，不主动提");
        assertThat(quiet).doesNotContain("这轮可能用得上的记录", "对方最近确认过的图片记录");

        String relevant = assembler.systemPrompt(11L, 7L, 5, null,
                new CompanionMemoryRecall.MemoryRecall(List.of(recalled), List.of(), 3));
        assertThat(relevant).contains("[对方上传的图片 #301 《骑士》 标签：剑 · 8月14日 · 视觉模型的观察与推测] 可见观察：一位少女");

        String asked = assembler.systemPrompt(11L, 7L, 5, null,
                new CompanionMemoryRecall.MemoryRecall(List.of(), List.of(recalled), 3));
        assertThat(asked).contains("## 对方最近确认过的图片记录", "- 《骑士》（8月14日）：可见观察：一位少女");

        String none = assembler.systemPrompt(11L, 7L, 5, null, CompanionMemoryRecall.MemoryRecall.empty());
        assertThat(none).contains("- 记忆：没有可用于对话的已确认图片记忆");
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void aNewConversationKnowsTheTimeAndLetsTheOldTopicGo() {
        Companion companion = Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        // 北京时间 10月10日 周六 15:20；上午 9:40 聊过秋招。
        Instant now = Instant.parse("2026-10-10T07:20:00Z");
        Instant morning = Instant.parse("2026-10-10T01:40:00Z");
        com.li.lipicturecloud.domain.companion.CompanionChatEpisode episode =
                new com.li.lipicturecloud.domain.companion.CompanionChatEpisode(5L, 11L, 7L, 40L, 41L, morning, morning,
                        "对方秋招投了三家，二面在下周。", "dashscope", "qwen3.8-max", morning);
        com.li.lipicturecloud.domain.companion.CompanionChatEpisode foreign =
                new com.li.lipicturecloud.domain.companion.CompanionChatEpisode(6L, 11L, 8L, 50L, 51L, morning, morning,
                        "别人的经历", "dashscope", "qwen3.8-max", morning);

        String prompt = assembler.systemPrompt(11L, 7L, 5, null, CompanionMemoryRecall.MemoryRecall.empty(),
                new CompanionChatContextAssembler.Conversation(now, morning, false, List.of(episode, foreign)));

        assertThat(prompt).contains("- 现在是 10月10日（周六） 15:20。",
                "这是新的一段对话，你们上次说话是约 5 小时前", "不要主动扯回去",
                "## 你记得的以前的聊天", "- 今天上午：对方秋招投了三家，二面在下周。", "不要拿别的事硬凑");
        assertThat(prompt).doesNotContain("别人的经历");
    }

    @Test
    void aRunningConversationIsNotTreatedAsNew() {
        Companion companion = Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        Instant now = Instant.parse("2026-10-10T07:20:00Z");

        String running = assembler.systemPrompt(11L, 7L, 5, null, CompanionMemoryRecall.MemoryRecall.empty(),
                new CompanionChatContextAssembler.Conversation(now, now.minusSeconds(180), true, List.of()));
        String first = assembler.systemPrompt(11L, 7L, 5, null, CompanionMemoryRecall.MemoryRecall.empty(),
                new CompanionChatContextAssembler.Conversation(now, null, false, List.of()));
        String legacy = assembler.systemPrompt(11L, 7L, 5, null, List.of());

        assertThat(running).contains("你们正在聊，上一句是 3 分钟前。").doesNotContain("新的一段对话", "以前的聊天");
        assertThat(first).contains("你们之前没有聊天记录。");
        assertThat(legacy).doesNotContain("## 时间");
    }

    @Test
    void episodeTimesAreDescribedRelativeToNow() {
        Instant now = Instant.parse("2026-10-10T07:20:00Z"); // 北京时间 15:20
        Instant afternoon = Instant.parse("2026-10-10T05:30:00Z"); // 13:30
        assertThat(CompanionChatContextAssembler.when(afternoon, afternoon, now)).isEqualTo("今天下午");
        // 一小时内刚结束的那段（比如退出又马上登录）就是"刚才"，不和下午更早的事混在一起。
        assertThat(CompanionChatContextAssembler.when(now.minusSeconds(900), now.minusSeconds(300), now)).isEqualTo("刚才");
        Instant lastNight = Instant.parse("2026-10-09T12:00:00Z");
        assertThat(CompanionChatContextAssembler.when(lastNight, lastNight, now)).isEqualTo("昨天晚上");
        Instant fourDays = Instant.parse("2026-10-06T02:00:00Z");
        assertThat(CompanionChatContextAssembler.when(fourDays, fourDays, now)).isEqualTo("4 天前");
        Instant longAgo = Instant.parse("2026-09-27T02:00:00Z");
        assertThat(CompanionChatContextAssembler.when(longAgo, longAgo, now)).isEqualTo("9月27日");
    }
}
