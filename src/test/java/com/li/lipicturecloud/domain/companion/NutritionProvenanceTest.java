package com.li.lipicturecloud.domain.companion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NutritionProvenanceTest {

    @Test
    void visualPolicyAcceptsVisualOrExplicitMetadataFallbackOnly() {
        NutritionProvenance visual = NutritionProvenance.visual(
                "dashscope", "qwen3.6-flash", "companion-vision-v1",
                "visual-observation-v1", new BigDecimal("0.82"));
        NutritionProvenance fallback = NutritionProvenance.metadataFallback("VISION_TIMEOUT");

        assertThat(NutritionPolicy.VISUAL_WITH_METADATA_FALLBACK.accepts(visual)).isTrue();
        assertThat(NutritionPolicy.VISUAL_WITH_METADATA_FALLBACK.accepts(fallback)).isTrue();
        assertThat(NutritionPolicy.METADATA_ONLY.accepts(visual)).isFalse();
    }

    @Test
    void rejectsVisualConfidenceOutsideClosedUnitInterval() {
        assertThatThrownBy(() -> NutritionProvenance.visual(
                "dashscope", "qwen3.6-flash", "companion-vision-v1",
                "visual-observation-v1", new BigDecimal("1.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
