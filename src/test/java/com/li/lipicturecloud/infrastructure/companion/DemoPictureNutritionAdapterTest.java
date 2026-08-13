package com.li.lipicturecloud.infrastructure.companion;

import com.li.lipicturecloud.application.companion.AuthorizedPictureRef;
import com.li.lipicturecloud.domain.companion.PictureNutrition;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DemoPictureNutritionAdapterTest {

    private final DemoPictureNutritionAdapter adapter = new DemoPictureNutritionAdapter();

    @ParameterizedTest
    @ValueSource(longs = {102L, 103L, 104L})
    void samePictureAlwaysProducesTheSameDisclosedDemoNutrition(long pictureId) {
        AuthorizedPictureRef picture = new AuthorizedPictureRef(AuthorizationSubject.user(7L), pictureId);

        PictureNutrition first = adapter.analyze(picture);
        PictureNutrition second = adapter.analyze(picture);

        assertThat(second).isEqualTo(first);
        assertThat(adapter.contentUnderstood()).isFalse();
        assertThat(first.reason()).contains("演示营养");
    }
}
