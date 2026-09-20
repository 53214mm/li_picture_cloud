package com.li.lipicturecloud.domain.airuntime;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreationFusionImageTest {

    private static final Instant NOW = Instant.parse("2026-08-15T08:00:00Z");
    private static final byte[] BYTES = {1, 2, 3};

    @Test
    void createValidatesAndCopiesBytesDefensively() {
        byte[] source = {9, 8, 7};
        CreationFusionImage image = CreationFusionImage.create(9L, "image/png", source, NOW);

        assertThat(image.taskId()).isEqualTo(9L);
        assertThat(image.mimeType()).isEqualTo("image/png");
        assertThat(image.bytes()).containsExactly(9, 8, 7);
        assertThat(image.createdTime()).isEqualTo(NOW);

        // 构造后修改源数组不影响已存字节；读出的副本修改也不影响内部字节。
        source[0] = 1;
        image.bytes()[0] = 2;
        assertThat(image.bytes()).containsExactly(9, 8, 7);
    }

    @Test
    void rejectsUnsupportedMimeTypesAndOversizedOrEmptyBytes() {
        assertThatThrownBy(() -> CreationFusionImage.create(9L, "image/gif", BYTES, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CreationFusionImage.create(9L, "image/jpeg", new byte[0], NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CreationFusionImage.create(9L, "image/jpeg", null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CreationFusionImage.create(9L, "image/jpeg",
                new byte[(int) CreationFusionImage.MAX_BYTES + 1], NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CreationFusionImage.create(0L, "image/png", BYTES, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CreationFusionImage.create(9L, "image/png", BYTES, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void withIdAssignsPersistedIdExactlyOnce() {
        CreationFusionImage image = CreationFusionImage.create(9L, "image/webp", BYTES, NOW);
        CreationFusionImage persisted = image.withId(3L);
        assertThat(persisted.id()).isEqualTo(3L);
        assertThatThrownBy(() -> persisted.withId(4L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> image.withId(0L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void maxSizeAllowsExactlySixteenMiB() {
        byte[] max = new byte[(int) CreationFusionImage.MAX_BYTES];
        CreationFusionImage image = CreationFusionImage.create(9L, "image/jpeg", max, NOW);
        assertThat(image.bytes()).hasSize((int) CreationFusionImage.MAX_BYTES);
    }
}
