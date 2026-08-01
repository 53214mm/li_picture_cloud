package com.li.lipicturecloud.domain.picture;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PictureAssetTest {

    @Test
    void distinguishesPublicAndSpaceOwnedPictures() {
        assertThat(new PictureAsset(1L, 2L, null).isPublic()).isTrue();
        assertThat(new PictureAsset(1L, 2L, 3L).isPublic()).isFalse();
    }

    @Test
    void requiresPictureAndOwnerIdentity() {
        assertThatThrownBy(() -> new PictureAsset(null, 2L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
