package com.li.lipicturecloud.domain.airuntime;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreationTaskTest {

    private static final Instant NOW = Instant.parse("2026-08-15T08:00:00Z");
    private static final String KEY = "fef53056-2d9f-467d-9b1d-1afe9a6638fe";

    @Test
    void createStartsPendingAtRevisionZero() {
        CreationTask task = CreationTask.create(7L, CreationKind.STORY_DRAFT,
                List.of(102L, 103L), KEY, NOW);

        assertThat(task.id()).isNull();
        assertThat(task.status()).isEqualTo(CreationStatus.PENDING);
        assertThat(task.sourcePictureIds()).containsExactly(102L, 103L);
        assertThat(task.revision()).isZero();
        assertThat(task.isTerminal()).isFalse();
    }

    @Test
    void happyPathStateMachineAdvancesRevisionByExactlyOne() {
        CreationTask task = CreationTask.create(7L, CreationKind.STORY_DRAFT, List.of(102L),
                KEY, NOW).withId(9L);

        CreationTask outlining = task.startOutlining(NOW);
        assertThat(outlining.revision()).isEqualTo(1L);

        CreationTask awaiting = outlining.completeOutline("开场：伙伴看见一缕光。", 5L, NOW);
        assertThat(awaiting.status()).isEqualTo(CreationStatus.AWAITING_CONFIRM);
        assertThat(awaiting.outlineText()).isEqualTo("开场：伙伴看见一缕光。");
        assertThat(awaiting.modelConnectionId()).isEqualTo(5L);
        assertThat(awaiting.revision()).isEqualTo(2L);

        CreationTask drafting = awaiting.confirmOutline(NOW);
        assertThat(drafting.status()).isEqualTo(CreationStatus.DRAFTING);

        CreationTask draftAwaiting = drafting.completeDraft("故事正文草稿。", NOW);
        assertThat(draftAwaiting.status()).isEqualTo(CreationStatus.AWAITING_CONFIRM);
        assertThat(draftAwaiting.draftText()).isEqualTo("故事正文草稿。");

        CreationTask saving = draftAwaiting.confirmDraft(NOW);
        assertThat(saving.status()).isEqualTo(CreationStatus.SAVING);

        CreationTask saved = saving.completeSave("最终作品文本。", NOW);
        assertThat(saved.status()).isEqualTo(CreationStatus.SAVED);
        assertThat(saved.isTerminal()).isTrue();
        assertThat(saved.revision()).isEqualTo(6L);
    }

    @Test
    void illegalTransitionsAreRejected() {
        CreationTask pending = CreationTask.create(7L, CreationKind.STORY_DRAFT, List.of(102L),
                KEY, NOW).withId(9L);
        assertThatThrownBy(() -> pending.completeOutline("x", null, NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending.confirmOutline(NOW))
                .isInstanceOf(IllegalStateException.class);

        CreationTask awaiting = pending.startOutlining(NOW)
                .completeOutline("大纲", null, NOW);
        assertThatThrownBy(() -> awaiting.confirmDraft(NOW))
                .isInstanceOf(IllegalStateException.class);

        CreationTask saved = awaiting.confirmOutline(NOW).completeDraft("草稿", NOW)
                .confirmDraft(NOW).completeSave("作品", NOW);
        assertThatThrownBy(() -> saved.fail(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> saved.startOutlining(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failAndExpireOnlyFromNonTerminalStates() {
        CreationTask drafting = CreationTask.create(7L, CreationKind.STORY_DRAFT, List.of(102L),
                KEY, NOW).withId(9L).startOutlining(NOW);
        CreationTask failed = drafting.fail(NOW);
        assertThat(failed.status()).isEqualTo(CreationStatus.FAILED);
        assertThat(failed.isTerminal()).isTrue();

        CreationTask awaiting = CreationTask.create(7L, CreationKind.STORY_DRAFT, List.of(102L),
                "fef53056-2d9f-467d-9b1d-1afe9a6638ff", NOW).withId(10L)
                .startOutlining(NOW).completeOutline("大纲", null, NOW);
        assertThat(awaiting.expire(NOW).status()).isEqualTo(CreationStatus.EXPIRED);
    }

    @Test
    void rejectsInvalidIdentitiesPicturesAndTexts() {
        assertThatThrownBy(() -> CreationTask.create(0L, CreationKind.STORY_DRAFT,
                List.of(102L), KEY, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CreationTask.create(7L, CreationKind.STORY_DRAFT,
                List.of(), KEY, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CreationTask.create(7L, CreationKind.STORY_DRAFT,
                List.of(102L, 102L), KEY, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CreationTask.create(7L, CreationKind.STORY_DRAFT,
                List.of(0L), KEY, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CreationTask.create(7L, CreationKind.STORY_DRAFT,
                java.util.Collections.nCopies(13, 1L), KEY, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CreationTask.create(7L, CreationKind.STORY_DRAFT,
                List.of(102L), "short", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CreationTask.create(7L, CreationKind.STORY_DRAFT,
                List.of(102L), null, NOW)).isInstanceOf(IllegalArgumentException.class);

        CreationTask task = CreationTask.create(7L, CreationKind.STORY_DRAFT, List.of(102L),
                KEY, NOW).withId(9L).startOutlining(NOW);
        assertThatThrownBy(() -> task.completeOutline("带\u0007控制字符", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> task.completeOutline("x".repeat(1001), null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> task.completeOutline("   ", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void selectDraftOnlyWorksForCandidateStyleAwaitingTasks() {
        CreationTask awaiting = new CreationTask(9L, 7L, CreationKind.EMOJI_DRAFT,
                List.of(102L), CreationStatus.AWAITING_CONFIRM, null, null, null, null, KEY,
                3L, NOW, NOW);
        CreationTask saving = awaiting.selectDraft("被选中表情", NOW);
        assertThat(saving.status()).isEqualTo(CreationStatus.SAVING);
        assertThat(saving.draftText()).isEqualTo("被选中表情");
        assertThat(saving.revision()).isEqualTo(4L);

        CreationTask storyAwaiting = new CreationTask(9L, 7L, CreationKind.STORY_DRAFT,
                List.of(102L), CreationStatus.AWAITING_CONFIRM, "大纲", null, null, null, KEY,
                3L, NOW, NOW);
        assertThatThrownBy(() -> storyAwaiting.selectDraft("x", NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> awaiting.selectDraft("带\u0007控制", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withIdAndTimestampGuards() {
        CreationTask created = CreationTask.create(7L, CreationKind.STORY_DRAFT, List.of(102L),
                KEY, NOW);
        CreationTask persisted = created.withId(9L);
        assertThat(persisted.id()).isEqualTo(9L);
        assertThatThrownBy(() -> persisted.withId(10L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> created.withId(0L)).isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> new CreationTask(9L, 7L, CreationKind.STORY_DRAFT,
                List.of(102L), CreationStatus.PENDING, null, null, null, 0L, KEY, 0L, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreationTask(9L, 7L, CreationKind.STORY_DRAFT,
                List.of(102L), CreationStatus.PENDING, null, null, null, null, KEY, 0L,
                NOW, NOW.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreationTask(9L, 7L, CreationKind.STORY_DRAFT,
                List.of(102L), CreationStatus.PENDING, null, null, null, null, KEY, 0L,
                NOW, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void wrongStateTransitionsAndOversizedTextsAreRejected() {
        CreationTask awaitingDraft = new CreationTask(9L, 7L, CreationKind.STORY_DRAFT,
                List.of(102L), CreationStatus.AWAITING_CONFIRM, "大纲", "草稿", null, null, KEY,
                3L, NOW, NOW);
        assertThatThrownBy(() -> awaitingDraft.confirmOutline(NOW))
                .isInstanceOf(IllegalStateException.class);

        CreationTask awaitingOutline = new CreationTask(9L, 7L, CreationKind.STORY_DRAFT,
                List.of(102L), CreationStatus.AWAITING_CONFIRM, "大纲", null, null, null, KEY,
                3L, NOW, NOW);
        assertThatThrownBy(() -> awaitingOutline.confirmDraft(NOW))
                .isInstanceOf(IllegalStateException.class);

        CreationTask drafting = awaitingOutline.confirmOutline(NOW);
        // 非等待确认状态不允许过期。
        assertThatThrownBy(() -> drafting.expire(NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> drafting.completeDraft("x".repeat(4001), NOW))
                .isInstanceOf(IllegalArgumentException.class);

        CreationTask saving = drafting.completeDraft("草稿", NOW).confirmDraft(NOW);
        assertThatThrownBy(() -> saving.completeSave("x".repeat(8001), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
