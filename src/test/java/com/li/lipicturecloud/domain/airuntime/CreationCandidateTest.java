package com.li.lipicturecloud.domain.airuntime;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreationCandidateTest {

    private static final Instant NOW = Instant.parse("2026-08-15T08:00:00Z");

    @Test
    void buildsValidCandidates() {
        CreationCandidate candidate = new CreationCandidate(null, 9L, 2, "今天的我很有精神！", NOW);
        assertThat(candidate.seq()).isEqualTo(2);
        assertThat(candidate.text()).isEqualTo("今天的我很有精神！");
    }

    @Test
    void rejectsInvalidSequencesAndTexts() {
        assertThatThrownBy(() -> new CreationCandidate(null, 0L, 0, "x", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreationCandidate(null, 9L, 8, "x", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreationCandidate(null, 9L, 0, "   ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreationCandidate(null, 9L, 0, "x".repeat(201), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreationCandidate(null, 9L, 0, "带\u0007控制", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreationCandidate(null, 9L, 0, "看 https://evil.test", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        // 链接检测大小写不敏感；双向/零宽/分隔符一律拒绝。
        assertThatThrownBy(() -> new CreationCandidate(null, 9L, 0, "看 HTTPS://EVIL.TEST", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreationCandidate(null, 9L, 0, "带\u202E双向控制", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreationCandidate(null, 9L, 0, "带\u200B零宽", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreationCandidate(null, 9L, 0, "带\u2028行分隔", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreationCandidate(null, 9L, 0, "x", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void withIdAssignsPersistedIdExactlyOnce() {
        CreationCandidate candidate = new CreationCandidate(null, 9L, 0, "x", NOW);
        CreationCandidate persisted = candidate.withId(3L);
        assertThat(persisted.id()).isEqualTo(3L);
        assertThatThrownBy(() -> persisted.withId(4L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> candidate.withId(0L)).isInstanceOf(IllegalStateException.class);
    }
}
