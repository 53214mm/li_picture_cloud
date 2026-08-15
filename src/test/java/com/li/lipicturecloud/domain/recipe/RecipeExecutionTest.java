package com.li.lipicturecloud.domain.recipe;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecipeExecutionTest {

    private static final Instant NOW = Instant.parse("2026-08-15T08:00:00Z");
    private static final Instant TRIGGERED = Instant.parse("2026-08-15T07:30:00Z");

    private static RecipeExecution dryRun() {
        return RecipeExecution.dryRun(9L, 1, 7L, TRIGGERED, "{\"when\":\"WEEKLY_REVIEW\"}",
                "{\"quote\":\"2\"}", NOW);
    }

    @Test
    void dryRunStartsWithoutTaskOrErrorCode() {
        RecipeExecution execution = dryRun();
        assertThat(execution.status()).isEqualTo(RecipeExecutionStatus.DRY_RUN);
        assertThat(execution.creationTaskId()).isNull();
        assertThat(execution.safeErrorCode()).isNull();
        assertThat(execution.isTerminal()).isFalse();
    }

    @Test
    void completesFailsAndRejectsAreTerminalTransitions() {
        RecipeExecution execution = dryRun().withId(5L);

        RecipeExecution completed = execution.complete(102L, NOW);
        assertThat(completed.status()).isEqualTo(RecipeExecutionStatus.EXECUTED);
        assertThat(completed.creationTaskId()).isEqualTo(102L);
        assertThat(completed.isTerminal()).isTrue();
        assertThatThrownBy(() -> completed.fail("UPSTREAM", NOW))
                .isInstanceOf(IllegalStateException.class);

        RecipeExecution failed = dryRun().withId(6L).fail("UPSTREAM_TIMEOUT", NOW);
        assertThat(failed.status()).isEqualTo(RecipeExecutionStatus.FAILED);
        assertThat(failed.safeErrorCode()).isEqualTo("UPSTREAM_TIMEOUT");

        RecipeExecution rejected = dryRun().withId(7L).reject("CONDITION_UNMATCHED", NOW);
        assertThat(rejected.status()).isEqualTo(RecipeExecutionStatus.REJECTED);
        assertThat(rejected.safeErrorCode()).isEqualTo("CONDITION_UNMATCHED");
    }

    @Test
    void rejectsInvalidTransitionsAndPayloads() {
        assertThatThrownBy(() -> dryRun().complete(0L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dryRun().fail("bad code!", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dryRun().fail(null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecipeExecution.dryRun(9L, 1, 7L, TRIGGERED,
                "带\u0007控制", "{}", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecipeExecution.dryRun(0L, 1, 7L, TRIGGERED, "{}", "{}", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecipeExecution.dryRun(9L, 0, 7L, TRIGGERED, "{}", "{}", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecipeExecution.dryRun(9L, 1, 7L, TRIGGERED, null, "{}", NOW))
                .isInstanceOf(NullPointerException.class);
        // EXECUTED 必须携带任务 ID 且不得携带错误码。
        assertThatThrownBy(() -> RecipeExecution.executed(9L, 1, 7L, TRIGGERED, "{}", "{}", 0L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
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
