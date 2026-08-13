package com.li.lipicturecloud.config;

import com.li.lipicturecloud.application.companion.PictureObservationProvider;
import com.li.lipicturecloud.domain.companion.NutritionMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CompanionConfigurationTest {

    private final CompanionConfiguration configuration = new CompanionConfiguration();
    private final PictureObservationProvider observations = mock(PictureObservationProvider.class);

    @Test
    void metadataNutritionIsTheLocalDefault() {
        CompanionFeatureProperties properties = new CompanionFeatureProperties();

        assertThat(configuration.pictureNutritionAnalyzer(properties, observations).mode())
                .isEqualTo(NutritionMode.METADATA_DETERMINISTIC);
    }

    @Test
    void demoModeCanBeSelectedForStableTestsAndE2e() {
        CompanionFeatureProperties properties = new CompanionFeatureProperties();
        properties.setNutritionMode(NutritionMode.DEMO_DETERMINISTIC);

        assertThat(configuration.pictureNutritionAnalyzer(properties, observations).mode())
                .isEqualTo(NutritionMode.DEMO_DETERMINISTIC);
    }
}
