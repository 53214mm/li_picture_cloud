package com.li.lipicturecloud.config;

import com.li.lipicturecloud.application.companion.AuthorizedPictureContentProvider;
import com.li.lipicturecloud.application.companion.PictureObservationProvider;
import com.li.lipicturecloud.application.companion.VisualObservationProvider;
import com.li.lipicturecloud.application.companion.VisionQuotaGuard;
import com.li.lipicturecloud.domain.companion.NutritionMode;
import com.li.lipicturecloud.domain.companion.NutritionPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CompanionConfigurationTest {

    private final CompanionConfiguration configuration = new CompanionConfiguration();
    private final PictureObservationProvider observations = mock(PictureObservationProvider.class);

    @Test
    void metadataNutritionIsTheLocalDefault() {
        assertThat(configuration.metadataPictureNutritionAnalyzer(observations).mode())
                .isEqualTo(NutritionMode.METADATA_DETERMINISTIC);
        assertThat(configuration.metadataPictureNutritionAnalyzer(observations).policy())
                .isEqualTo(NutritionPolicy.METADATA_ONLY);
    }

    @Test
    void demoModeCanBeSelectedForStableTestsAndE2e() {
        assertThat(configuration.demoPictureNutritionAnalyzer().mode())
                .isEqualTo(NutritionMode.DEMO_DETERMINISTIC);
    }

    @Test
    void visualModeWiresTheGuardedAdapterWithoutCreatingItForOtherModes() {
        CompanionFeatureProperties properties = new CompanionFeatureProperties();
        VisionQuotaGuard quota = mock(VisionQuotaGuard.class);
        AuthorizedPictureContentProvider contents = mock(AuthorizedPictureContentProvider.class);
        VisualObservationProvider visual = mock(VisualObservationProvider.class);

        assertThat(configuration.visualPictureNutritionAnalyzer(properties, observations, quota, contents, visual,
                Clock.systemUTC()).policy()).isEqualTo(NutritionPolicy.VISUAL_WITH_METADATA_FALLBACK);
    }
}
