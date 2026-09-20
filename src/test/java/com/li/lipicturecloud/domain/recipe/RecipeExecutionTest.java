package com.li.lipicturecloud.domain.recipe;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecipeExecutionTest {

    private static final Instant NOW = Instant.parse("2026-08-15T08:00:00Z");
    private static final Instant TRIGGERED = Instant.parse("2026-08-15T07:30:00Z");
    private static final String SNAPSHOT = RecipeExecution.snapshotJson(List.of(102L, 103L));

    private static RecipeExecution dryRun() {
        return RecipeExecution.dryRun(9L, 1, 7L, TRIGGERED, "{\"when\":\"WEEKLY_REVIEW\"}",
                "{\"quote\":\"2\"}", SNAPSHOT, NOW);
    }

    @Test
    void dryRunStartsWithoutTaskOrErrorCode() {
        RecipeExecution execution = dryRun();
        assertThat(execution.status()).isEqualTo(RecipeExecutionStatus.DRY_RUN);
        assertThat(execution.creationTaskId()).isNull();
        assertThat(execution.safeErrorCode()).isNull();
        assertThat(execution.isTerminal()).isFalse();
        assertThat(execution.isAwaitingConfirm()).isTrue();
        // 试运行记录绑定来源图片快照，确认执行只认这份快照。
        assertThat(execution.sourcePictureIds()).containsExactly(102L, 103L);
        assertThat(execution.opportunityKey()).isNull();
    }

    @Test
    void pendingRecordsCarryTheOpportunityKeyAndAwaitConfirmation() {
        RecipeExecution pending = RecipeExecution.pending(9L, 1, 7L, TRIGGERED,
                "{\"when\":\"WEEKLY_REVIEW\"}", "{\"quote\":\"2\"}",
                RecipeExecution.snapshotJson(List.of(102L)), "WEEKLY_REVIEW-2026-W33", NOW);

        assertThat(pending.status()).isEqualTo(RecipeExecutionStatus.PENDING_CONFIRM);
        assertThat(pending.isTerminal()).isFalse();
        assertThat(pending.isAwaitingConfirm()).isTrue();
        assertThat(pending.opportunityKey()).isEqualTo("WEEKLY_REVIEW-2026-W33");
        assertThat(pending.creationTaskId()).isNull();
        assertThat(pending.safeErrorCode()).isNull();

        RecipeExecution confirmed = pending.withId(5L).complete(102L, "{\"when\":\"WEEKLY_REVIEW\"}",
                "{\"platformUnits\":5}", NOW);
        assertThat(confirmed.status()).isEqualTo(RecipeExecutionStatus.EXECUTED);
        // 快照与机会键随确认执行保留，回放能看到这条记录来自哪个机会。
        assertThat(confirmed.sourcePictureIds()).containsExactly(102L);
        assertThat(confirmed.opportunityKey()).isEqualTo("WEEKLY_REVIEW-2026-W33");
    }

    @Test
    void completesFailsAndRejectsAreTerminalTransitions() {
        RecipeExecution execution = dryRun().withId(5L);

        RecipeExecution completed = execution.complete(102L, "{\"when\":\"WEEKLY_REVIEW\"}",
                "{\"platformUnits\":1}", NOW);
        assertThat(completed.status()).isEqualTo(RecipeExecutionStatus.EXECUTED);
        assertThat(completed.creationTaskId()).isEqualTo(102L);
        assertThat(completed.isTerminal()).isTrue();
        assertThatThrownBy(() -> completed.fail("UPSTREAM", "{}", "{}", NOW))
                .isInstanceOf(IllegalStateException.class);

        RecipeExecution failed = dryRun().withId(6L)
                .fail("UPSTREAM_TIMEOUT", "{}", "{}", NOW);
        assertThat(failed.status()).isEqualTo(RecipeExecutionStatus.FAILED);
        assertThat(failed.safeErrorCode()).isEqualTo("UPSTREAM_TIMEOUT");

        RecipeExecution rejected = dryRun().withId(7L)
                .reject("CONDITION_UNMATCHED", "{}", "{}", NOW);
        assertThat(rejected.status()).isEqualTo(RecipeExecutionStatus.REJECTED);
        assertThat(rejected.safeErrorCode()).isEqualTo("CONDITION_UNMATCHED");
    }

    @Test
    void rejectsInvalidTransitionsAndPayloads() {
        assertThatThrownBy(() -> dryRun().complete(0L, "{}", "{}", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dryRun().fail("bad code!", "{}", "{}", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dryRun().fail(null, "{}", "{}", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dryRun().reject("CODE", null, "{}", NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> dryRun().reject("CODE", "带\u0007控制", "{}", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecipeExecution.dryRun(9L, 1, 7L, TRIGGERED,
                "带\u0007控制", "{}", SNAPSHOT, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecipeExecution.dryRun(0L, 1, 7L, TRIGGERED, "{}", "{}",
                SNAPSHOT, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecipeExecution.dryRun(9L, 0, 7L, TRIGGERED, "{}", "{}",
                SNAPSHOT, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecipeExecution.dryRun(9L, 1, 7L, TRIGGERED, null, "{}",
                SNAPSHOT, NOW))
                .isInstanceOf(NullPointerException.class);
        // EXECUTED 必须携带任务 ID 且不得携带错误码。
        assertThatThrownBy(() -> RecipeExecution.executed(9L, 1, 7L, TRIGGERED, "{}", "{}",
                SNAPSHOT, 0L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pictureSnapshotsAndOpportunityKeysAreBoundedSafeValues() {
        assertThat(RecipeExecution.snapshotJson(List.of(102L, 102L, 103L)))
                .isEqualTo("[102,103]");
        assertThatThrownBy(() -> RecipeExecution.snapshotJson(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecipeExecution.snapshotJson(List.of(0L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecipeExecution.snapshotJson(
                java.util.stream.LongStream.rangeClosed(1, 13).boxed().toList()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> RecipeExecution.dryRun(9L, 1, 7L, TRIGGERED, "{}", "{}",
                "[102, 103]", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecipeExecution.dryRun(9L, 1, 7L, TRIGGERED, "{}", "{}",
                "[a]", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecipeExecution.pending(9L, 1, 7L, TRIGGERED, "{}", "{}",
                SNAPSHOT, "bad key!", NOW)).isInstanceOf(IllegalArgumentException.class);
        // 空白快照与空白键按"没有快照/没有键"处理，而不是非法值。
        assertThat(RecipeExecution.dryRun(9L, 1, 7L, TRIGGERED, "{}", "{}", "  ", NOW)
                .sourcePictureIds()).isEmpty();
        assertThat(RecipeExecution.pending(9L, 1, 7L, TRIGGERED, "{}", "{}", SNAPSHOT, " ", NOW)
                .opportunityKey()).isNull();
    }

    @Test
    void withIdAssignsPersistedIdExactlyOnce() {
        RecipeExecution created = dryRun();
        RecipeExecution persisted = created.withId(3L);
        assertThat(persisted.id()).isEqualTo(3L);
        assertThatThrownBy(() -> persisted.withId(4L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> created.withId(0L)).isInstanceOf(IllegalStateException.class);
    }
}
