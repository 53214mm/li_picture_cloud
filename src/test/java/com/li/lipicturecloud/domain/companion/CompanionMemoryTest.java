package com.li.lipicturecloud.domain.companion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanionMemoryTest {

    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

    @Test
    void candidateStartsPendingWithSameOriginalAndCurrentContent() {
        CompanionMemory memory = CompanionMemory.candidate(11L, 7L, 101L, 21L,
                MemorySourceType.VISUAL, "伙伴记得这张图片带给它的明亮感受，它把这种感受记进了档案。",
                new BigDecimal("0.84"), NOW);

        assertThat(memory.status()).isEqualTo(MemoryStatus.PENDING);
        assertThat(memory.content()).isEqualTo(memory.originalContent());
        assertThat(memory.confidence()).isEqualByComparingTo("0.840");
        assertThat(memory.revision()).isZero();
        assertThat(memory.exposesContent()).isTrue();
        assertThat(memory.active()).isTrue();
    }

    @Test
    void confirmTransitionsPendingAndDismissedToConfirmed() {
        CompanionMemory pending = candidate();
        CompanionMemory confirmed = pending.confirm(NOW.plusSeconds(60));

        assertThat(confirmed.status()).isEqualTo(MemoryStatus.CONFIRMED);
        assertThat(confirmed.revision()).isEqualTo(1L);
        assertThat(confirmed.originalContent()).isEqualTo(pending.originalContent());

        CompanionMemory dismissed = confirmed.dismiss(NOW.plusSeconds(120));
        assertThat(dismissed.status()).isEqualTo(MemoryStatus.DISMISSED);
        assertThat(dismissed.confirm(NOW.plusSeconds(180)).status()).isEqualTo(MemoryStatus.CONFIRMED);
    }

    @Test
    void correctRewritesContentAndKeepsOriginalCandidate() {
        CompanionMemory pending = candidate();

        CompanionMemory corrected = pending.correct("伙伴重新想起：那张图其实让它想起安静的清晨。", NOW);

        assertThat(corrected.status()).isEqualTo(MemoryStatus.CONFIRMED);
        assertThat(corrected.content()).isEqualTo("伙伴重新想起：那张图其实让它想起安静的清晨。");
        assertThat(corrected.originalContent()).isEqualTo(pending.originalContent());
        assertThat(corrected.revision()).isEqualTo(1L);
    }

    @Test
    void invalidateRequiresReasonAndHidesContent() {
        CompanionMemory pending = candidate();

        CompanionMemory invalidated = pending.invalidate("PICTURE_UNAVAILABLE", NOW);

        assertThat(invalidated.status()).isEqualTo(MemoryStatus.INVALIDATED);
        assertThat(invalidated.invalidatedReason()).isEqualTo("PICTURE_UNAVAILABLE");
        assertThat(invalidated.exposesContent()).isFalse();
        assertThat(invalidated.active()).isFalse();
    }

    @Test
    void deleteIsTerminalFromAnyActiveState() {
        CompanionMemory confirmed = candidate().confirm(NOW);
        CompanionMemory deleted = confirmed.delete(NOW);

        assertThat(deleted.status()).isEqualTo(MemoryStatus.DELETED);
        assertThat(deleted.invalidatedReason()).isNull();
        assertThat(deleted.exposesContent()).isFalse();
    }

    @Test
    void terminalStatesRejectFurtherTransitions() {
        CompanionMemory invalidated = candidate().invalidate("PICTURE_UNAVAILABLE", NOW);
        assertThatThrownBy(() -> invalidated.confirm(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> invalidated.dismiss(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> invalidated.correct("改一下", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> invalidated.invalidate("AGAIN", NOW)).isInstanceOf(IllegalStateException.class);

        CompanionMemory deleted = candidate().delete(NOW);
        assertThatThrownBy(() -> deleted.confirm(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnsafeOrOversizedContent() {
        assertThatThrownBy(() -> CompanionMemory.candidate(11L, 7L, 101L, 21L,
                MemorySourceType.VISUAL, "包含 http://example.com 链接的记忆", bd("0.5"), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompanionMemory.candidate(11L, 7L, 101L, 21L,
                MemorySourceType.VISUAL, "首行\u0000控制字符", bd("0.5"), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompanionMemory.candidate(11L, 7L, 101L, 21L,
                MemorySourceType.VISUAL, "长".repeat(301), bd("0.5"), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompanionMemory.candidate(11L, 7L, 101L, 21L,
                MemorySourceType.VISUAL, "   ", bd("0.5"), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOutOfRangeConfidenceAndInvalidStates() {
        assertThatThrownBy(() -> CompanionMemory.candidate(11L, 7L, 101L, 21L,
                MemorySourceType.VISUAL, "正常的记忆文案", new BigDecimal("1.01"), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompanionMemory(null, 11L, 7L, 101L, 21L,
                MemorySourceType.VISUAL, "内容", "内容", bd("0.5"),
                MemoryStatus.CONFIRMED, "不应存在的理由", 0L, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static CompanionMemory candidate() {
        return CompanionMemory.candidate(11L, 7L, 101L, 21L, MemorySourceType.VISUAL,
                "伙伴记得这张图片带给它的明亮感受，它把这种感受记进了档案。",
                new BigDecimal("0.84"), NOW);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
