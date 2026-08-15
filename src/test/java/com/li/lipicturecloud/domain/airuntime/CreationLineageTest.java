package com.li.lipicturecloud.domain.airuntime;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreationLineageTest {

    private static final Instant NOW = Instant.parse("2026-08-15T08:00:00Z");

    @Test
    void buildsValidLineageRows() {
        CreationLineage textLineage = new CreationLineage(null, 9L, 102L, null,
                "STORY_DRAFT_OUTLINE", "demo-v1", "story-v1", "PLATFORM", NOW);

        assertThat(textLineage.taskId()).isEqualTo(9L);
        assertThat(textLineage.sourcePictureId()).isEqualTo(102L);
        assertThat(textLineage.resultPictureId()).isNull();
        assertThat(textLineage.costSource()).isEqualTo("PLATFORM");

        CreationLineage fusionLineage = new CreationLineage(null, 9L, 102L, 300L,
                "IMAGE_FUSION_SAVE", "gpt-image-2", "fusion-v1", "BYOK", NOW);
        assertThat(fusionLineage.resultPictureId()).isEqualTo(300L);
    }

    @Test
    void rejectsInvalidFields() {
        assertThatThrownBy(() -> new CreationLineage(null, 0L, 102L, null, "CAP", "m", "v", "S", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreationLineage(null, 9L, 0L, null, "CAP", "m", "v", "S", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreationLineage(null, 9L, 102L, 0L, "CAP", "m", "v", "S", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreationLineage(null, 9L, 102L, null, "bad id", "m", "v", "S", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreationLineage(null, 9L, 102L, null, "CAP", "bad model", "v", "S", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreationLineage(null, 9L, 102L, null, "CAP", "m", "bad version!", "S", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreationLineage(null, 9L, 102L, null, "CAP", "m", "v", "FREE!", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreationLineage(0L, 9L, 102L, null, "CAP", "m", "v", "S", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreationLineage(-1L, 9L, 102L, null, "CAP", "m", "v", "S", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreationLineage(null, 9L, 102L, null, null, "m", "v", "S", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreationLineage(null, 9L, 102L, null, "CAP", "m", "v", "S", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void withIdAssignsPersistedIdExactlyOnce() {
        CreationLineage lineage = new CreationLineage(null, 9L, 102L, null, "STORY_DRAFT_OUTLINE",
                "demo-v1", "story-v1", "PLATFORM", NOW);
        CreationLineage persisted = lineage.withId(3L);
        assertThat(persisted.id()).isEqualTo(3L);
        assertThatThrownBy(() -> persisted.withId(4L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> lineage.withId(0L)).isInstanceOf(IllegalStateException.class);
    }
}
