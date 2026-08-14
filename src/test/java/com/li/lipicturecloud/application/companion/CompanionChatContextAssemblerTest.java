package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.domain.companion.CompanionMemory;
import com.li.lipicturecloud.domain.companion.CompanionMemoryRepository;
import com.li.lipicturecloud.domain.companion.CompanionMood;
import com.li.lipicturecloud.domain.companion.CompanionMoodRepository;
import com.li.lipicturecloud.domain.companion.CompanionRelationship;
import com.li.lipicturecloud.domain.companion.CompanionRelationshipRepository;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
import com.li.lipicturecloud.domain.companion.MemorySourceType;
import com.li.lipicturecloud.domain.companion.MemoryStatus;
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
    private CompanionMoodRepository moodRepository;
    private CompanionRelationshipRepository relationshipRepository;
    private CompanionMemoryRepository memoryRepository;
    private CompanionChatContextAssembler assembler;

    @BeforeEach
    void setUp() {
        companionRepository = mock(CompanionRepository.class);
        moodRepository = mock(CompanionMoodRepository.class);
        relationshipRepository = mock(CompanionRelationshipRepository.class);
        memoryRepository = mock(CompanionMemoryRepository.class);
        assembler = new CompanionChatContextAssembler(companionRepository, moodRepository,
                relationshipRepository, memoryRepository);
    }

    @Test
    void promptContainsOnlyPersistedFactsAndConfirmedMemories() {
        Companion companion = Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
        CompanionMemory confirmed = CompanionMemory.candidate(11L, 7L, 101L, 21L,
                MemorySourceType.VISUAL, "伙伴记得一张明亮的图片。", new BigDecimal("0.8"), NOW)
                .confirm(NOW);
        CompanionMemory pending = CompanionMemory.candidate(11L, 7L, 102L, 22L,
                MemorySourceType.VISUAL, "待确认的记忆不应进入上下文。", new BigDecimal("0.5"), NOW);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(moodRepository.findByCompanionId(11L))
                .thenReturn(Optional.of(new CompanionMood(51L, 11L,
                        bd("30"), bd("20"), bd("0"), bd("10"), bd("0"), 1L, NOW)));
        when(relationshipRepository.findByCompanionAndSubject(11L, 7L))
                .thenReturn(Optional.of(CompanionRelationship.restore(61L, 11L, 7L,
                        bd("40"), bd("20"), bd("10"), bd("5"), bd("3"), 2L)));
        when(memoryRepository.findRecent(11L, 100))
                .thenReturn(List.of(pending, confirmed));

        String prompt = assembler.systemPrompt(11L, 7L, 5);

        assertThat(prompt).contains("光点");
        assertThat(prompt).contains("伙伴记得一张明亮的图片。");
        assertThat(prompt).doesNotContain("待确认的记忆不应进入上下文");
        assertThat(prompt).contains("只能引用下面的记忆");
    }

    @Test
    void promptStatesNoMemoriesInsteadOfFabricating() {
        Companion companion = Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(memoryRepository.findRecent(11L, 100)).thenReturn(List.of());

        String prompt = assembler.systemPrompt(11L, 7L, 5);

        assertThat(prompt).contains("还没有确认的记忆");
        assertThat(prompt).contains("编造");
    }

    @Test
    void longMemoryContentIsTruncatedForThePrompt() {
        Companion companion = Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
        // 204 码点：在记忆 300 上限内，但超过上下文的 120 截断线。
        String longContent = "伙伴记得".concat("内容".repeat(100));
        CompanionMemory confirmed = CompanionMemory.candidate(11L, 7L, 101L, 21L,
                MemorySourceType.VISUAL, longContent, new BigDecimal("0.8"), NOW).confirm(NOW);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(memoryRepository.findRecent(11L, 100)).thenReturn(List.of(confirmed));

        String prompt = assembler.systemPrompt(11L, 7L, 5);

        // 截断标记存在，且完整原文（204 码点）不会整段出现在提示词中。
        assertThat(prompt).contains("……");
        assertThat(prompt).doesNotContain(longContent);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
