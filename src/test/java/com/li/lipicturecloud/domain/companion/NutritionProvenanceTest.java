package com.li.lipicturecloud.domain.companion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

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
        assertThat(NutritionPolicy.VISUAL_WITH_METADATA_FALLBACK.accepts(NutritionProvenance.metadata())).isFalse();
        assertThat(NutritionPolicy.VISUAL_WITH_METADATA_FALLBACK.accepts(NutritionProvenance.demo())).isFalse();
        assertThat(NutritionPolicy.METADATA_ONLY.accepts(visual)).isFalse();
        assertThat(NutritionPolicy.METADATA_ONLY.accepts(NutritionProvenance.metadata())).isTrue();
        assertThat(NutritionPolicy.METADATA_ONLY.accepts(fallback)).isFalse();
        assertThat(NutritionPolicy.DEMO_ONLY.accepts(NutritionProvenance.demo())).isTrue();
        assertThat(NutritionPolicy.DEMO_ONLY.accepts(NutritionProvenance.metadata())).isFalse();
        assertThatThrownBy(() -> NutritionPolicy.DEMO_ONLY.accepts(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsVisualConfidenceOutsideClosedUnitInterval() {
        assertThat(NutritionProvenance.visual(
                "dashscope", "qwen3.6-flash", "companion-vision-v1",
                "visual-observation-v1", BigDecimal.ZERO).confidence()).isEqualByComparingTo("0");
        assertThat(NutritionProvenance.visual(
                "dashscope", "qwen3.6-flash", "companion-vision-v1",
                "visual-observation-v1", BigDecimal.ONE).confidence()).isEqualByComparingTo("1");
        assertThatThrownBy(() -> NutritionProvenance.visual(
                "dashscope", "qwen3.6-flash", "companion-vision-v1",
                "visual-observation-v1", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NutritionProvenance.visual(
                "dashscope", "qwen3.6-flash", "companion-vision-v1",
                "visual-observation-v1", new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NutritionProvenance.visual(
                "dashscope", "qwen3.6-flash", "companion-vision-v1",
                "visual-observation-v1", new BigDecimal("1.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsContradictoryOrUnsafeAuditFacts() {
        assertThatThrownBy(() -> new NutritionProvenance(
                NutritionMode.VISUAL_MODEL, false, "dashscope", "qwen3.6-flash",
                "companion-vision-v1", "visual-observation-v1", new BigDecimal("0.8"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NutritionProvenance(
                NutritionMode.VISUAL_MODEL, true, "dashscope", "qwen3.6-flash",
                "companion-vision-v1", "visual-observation-v1", new BigDecimal("0.8"), "VISION_TIMEOUT"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NutritionProvenance(
                NutritionMode.METADATA_DETERMINISTIC, true, "internal", "metadata-v1",
                "none", "nutrition-v1", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NutritionProvenance(
                NutritionMode.METADATA_DETERMINISTIC, false, "internal", "metadata-v1",
                "none", "nutrition-v1", new BigDecimal("0.5"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NutritionProvenance(
                NutritionMode.DEMO_DETERMINISTIC, false, "internal", "demo-v1",
                "none", "nutrition-v1", null, "VISION_TIMEOUT"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NutritionProvenance(
                NutritionMode.DEMO_DETERMINISTIC, false, null, "demo-v1",
                "none", "nutrition-v1", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NutritionProvenance(
                NutritionMode.DEMO_DETERMINISTIC, false, "internal", "bad/model",
                "none", "nutrition-v1", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void convertsEveryLegacyModeWithoutChangingItsMeaning() {
        assertThat(NutritionPolicy.fromLegacyMode(NutritionMode.DEMO_DETERMINISTIC))
                .isEqualTo(NutritionPolicy.DEMO_ONLY);
        assertThat(NutritionPolicy.fromLegacyMode(NutritionMode.METADATA_DETERMINISTIC))
                .isEqualTo(NutritionPolicy.METADATA_ONLY);
        assertThat(NutritionPolicy.fromLegacyMode(NutritionMode.VISUAL_MODEL))
                .isEqualTo(NutritionPolicy.VISUAL_WITH_METADATA_FALLBACK);
        assertThatThrownBy(() -> NutritionPolicy.fromLegacyMode(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void legacyNutritionConstructorCannotFabricateVisualUnderstanding() {
        PictureNutrition demo = new PictureNutrition(1L, TraitDelta.zero(), Map.of(), "demo",
                NutritionMode.DEMO_DETERMINISTIC, false);
        PictureNutrition metadata = new PictureNutrition(1L, TraitDelta.zero(), Map.of(), "metadata",
                NutritionMode.METADATA_DETERMINISTIC, false);

        assertThat(demo.provenance()).isEqualTo(NutritionProvenance.demo());
        assertThat(metadata.provenance()).isEqualTo(NutritionProvenance.metadata());
        assertThatThrownBy(() -> new PictureNutrition(1L, TraitDelta.zero(), Map.of(), "invalid",
                NutritionMode.DEMO_DETERMINISTIC, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PictureNutrition(1L, TraitDelta.zero(), Map.of(), "invalid",
                NutritionMode.VISUAL_MODEL, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PictureNutrition(1L, TraitDelta.zero(), Map.of(), "invalid",
                null, false))
                .isInstanceOf(NullPointerException.class);
    }
}
