package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.domain.companion.CompanionMemory;
import com.li.lipicturecloud.domain.companion.CompanionMood;
import com.li.lipicturecloud.domain.companion.CompanionRelationship;
import com.li.lipicturecloud.domain.companion.CompanionRelationshipRepository;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
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

        assertThat(prompt).contains("光点");
        // 调用方保证可用记忆已通过撤权检查；这里仍防御性只放行 CONFIRMED。
        assertThat(prompt).contains("伙伴记得一张明亮的图片。");
        assertThat(prompt).doesNotContain("待确认的记忆不应进入上下文");
        assertThat(prompt).contains("精力 30、愉悦 20、孤独 0、灵感 10、烦躁 0");
        assertThat(prompt).contains("只能引用下面的记忆");
    }

    @Test
    void promptStatesNoMemoriesAndNeutralMoodInsteadOfFabricating() {
        Companion companion = Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));

        String prompt = assembler.systemPrompt(11L, 7L, 5, null, List.of());

        assertThat(prompt).contains("还没有确认的记忆");
        assertThat(prompt).contains("编造");
        assertThat(prompt).contains("还没有明显情绪");
    }

    @Test
    void longMemoryContentIsTruncatedForThePrompt() {
        Companion companion = Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
        // 204 码点：在记忆 300 上限内，但超过上下文的 120 截断线。
        String longContent = "伙伴记得".concat("内容".repeat(100));
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

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
