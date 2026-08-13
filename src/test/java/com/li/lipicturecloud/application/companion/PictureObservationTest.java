package com.li.lipicturecloud.application.companion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PictureObservationTest {

    @Test
    void normalizesFormatWithoutKeepingBlankValues() {
        assertThat(new PictureObservation(1L, false, false,
                100, 50, 10L, " JPEG ").format()).isEqualTo("jpeg");
        assertThat(new PictureObservation(1L, false, false,
                null, null, null, "  ").format()).isNull();
    }

    @Test
    void rejectsPartialOrInvalidDimensions() {
        assertThatThrownBy(() -> new PictureObservation(
                1L, false, false, 100, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PictureObservation(
                1L, false, false, 0, 10, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
