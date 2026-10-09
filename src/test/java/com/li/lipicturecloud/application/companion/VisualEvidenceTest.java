package com.li.lipicturecloud.application.companion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VisualEvidenceTest {
    private static final String OBSERVATION = "画面中可见一只猫停留在窗台旁，窗外有绿色树木和明亮天空。";

    @Test
    void acceptsNoInferenceAndDerivesCompatibilitySummaryOnlyFromEvidence() {
        VisualEvidence evidence = new VisualEvidence(OBSERVATION, "");
        VisualObservationCandidate candidate = new VisualObservationCandidate(VisualObservationCandidate.Mood.CALM,
                2, 1, false, 0, 1, BigDecimal.ONE, "ignored legacy text", evidence);
        assertThat(candidate.companionMessage()).isEqualTo("可见观察：" + OBSERVATION);
        assertThat(candidate.evidence()).isEqualTo(evidence);
    }

    @Test
    void rejectsFirstPersonUnsafeAndOverBudgetFields() {
        for (String invalid : new String[]{"我看见" + OBSERVATION, "绫页觉得" + OBSERVATION,
                "I can see " + OBSERVATION, OBSERVATION + "https://example.test", OBSERVATION + "\n", "猫".repeat(161)}) {
            // Trailing whitespace is normalized; embedded controls are rejected.
            String value = invalid.endsWith("\n") ? OBSERVATION + "\n天空" : invalid;
            assertThatThrownBy(() -> new VisualEvidence(value, "")).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new VisualEvidence(OBSERVATION, "可能".repeat(31)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legacyConstructorRemainsReadableWithoutInventingEvidence() {
        VisualObservationCandidate legacy = new VisualObservationCandidate(VisualObservationCandidate.Mood.CALM,
                2, 1, false, 0, 1, BigDecimal.ONE, "我从这张图片里感到平静，这是历史版本保留的伙伴独白。");
        assertThat(legacy.evidence()).isNull();
        assertThat(legacy.companionMessage()).startsWith("我");
    }
}
