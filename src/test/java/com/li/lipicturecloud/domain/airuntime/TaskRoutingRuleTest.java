package com.li.lipicturecloud.domain.airuntime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskRoutingRuleTest {

    @Test
    void createYieldsRuleAtRevisionZero() {
        TaskRoutingRule rule = TaskRoutingRule.create(7L, ModelTask.LANGUAGE_AGENT, 11L);

        assertThat(rule.id()).isNull();
        assertThat(rule.subjectId()).isEqualTo(7L);
        assertThat(rule.task()).isEqualTo(ModelTask.LANGUAGE_AGENT);
        assertThat(rule.connectionId()).isEqualTo(11L);
        assertThat(rule.revision()).isZero();
    }

    @Test
    void createAllowsNullConnectionIdForPlatformDefault() {
        TaskRoutingRule rule = TaskRoutingRule.create(7L, ModelTask.IMAGE_CREATION, null);
        assertThat(rule.connectionId()).isNull();
    }

    @Test
    void rejectsInvalidIdentitiesAndRevisions() {
        assertThatThrownBy(() -> TaskRoutingRule.create(0L, ModelTask.LANGUAGE_AGENT, 11L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TaskRoutingRule.create(7L, null, 11L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TaskRoutingRule.create(7L, ModelTask.LANGUAGE_AGENT, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TaskRoutingRule.restore(null, 7L, ModelTask.LANGUAGE_AGENT,
                11L, 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TaskRoutingRule.restore(0L, 7L, ModelTask.LANGUAGE_AGENT,
                11L, 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TaskRoutingRule.restore(1L, 7L, ModelTask.LANGUAGE_AGENT,
                11L, -1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withIdAssignsPersistedIdExactlyOnce() {
        TaskRoutingRule created = TaskRoutingRule.create(7L, ModelTask.LANGUAGE_AGENT, 11L);

        TaskRoutingRule persisted = created.withId(11L);
        assertThat(persisted.id()).isEqualTo(11L);

        assertThatThrownBy(() -> persisted.withId(12L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> created.withId(0L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void routeToAdvancesRevisionByExactlyOne() {
        TaskRoutingRule rule = TaskRoutingRule.create(7L, ModelTask.LANGUAGE_AGENT, 11L)
                .withId(5L);

        TaskRoutingRule routed = rule.routeTo(22L);
        assertThat(routed.connectionId()).isEqualTo(22L);
        assertThat(routed.revision()).isEqualTo(1L);

        assertThat(routed.routeTo(null).connectionId()).isNull();
        assertThat(routed.routeTo(null).revision()).isEqualTo(2L);
    }

    @Test
    void routeToOverflowIsRejected() {
        TaskRoutingRule rule = TaskRoutingRule.restore(5L, 7L, ModelTask.LANGUAGE_AGENT,
                11L, Long.MAX_VALUE);
        assertThatThrownBy(() -> rule.routeTo(22L)).isInstanceOf(ArithmeticException.class);
    }
}
