package com.li.lipicturecloud.infrastructure.companion;

import com.li.lipicturecloud.application.companion.AuthorizedPictureRef;
import com.li.lipicturecloud.application.companion.PictureObservation;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import com.li.lipicturecloud.model.entity.Picture;
import com.li.lipicturecloud.repository.PictureRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import static com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant.PICTURE_VIEW;

class MetadataPictureObservationProviderTest {

    private final PictureRepository pictures = mock(PictureRepository.class);
    private final SpaceAuthorizationAccessService authorization = mock(SpaceAuthorizationAccessService.class);
    private final MetadataPictureObservationProvider provider =
            new MetadataPictureObservationProvider(pictures, authorization);
    private final AuthorizedPictureRef authorized =
            new AuthorizedPictureRef(AuthorizationSubject.user(7L), 102L);

    @Test
    void exposesOnlyNormalizedMetadataNeededByTheNutritionPolicy() {
        Picture picture = new Picture();
        picture.setId(102L);
        picture.setIntroduction("海边旅行");
        picture.setCategory("旅行");
        picture.setPicWidth(1920);
        picture.setPicHeight(1080);
        picture.setPicSize(2_048_000L);
        picture.setPicFormat(" JPEG ");
        picture.setOriginalUrl("https://private.example/secret.jpg");
        when(pictures.findById(102L)).thenReturn(Optional.of(picture));

        PictureObservation observation = provider.observe(authorized);

        assertThat(observation).isEqualTo(new PictureObservation(
                102L, true, true, 1920, 1080, 2_048_000L, "jpeg"));
        assertThat(observation.toString()).doesNotContain("secret.jpg", "海边旅行", "旅行");
        verify(authorization).checkForUser(PICTURE_VIEW, 102L, 7L);
    }

    @Test
    void missingPictureFailsWithoutInventingAnObservation() {
        when(pictures.findById(102L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.observe(authorized))
                .isInstanceOf(BusinessException.class)
                .hasMessage("图片不可用或无权访问");
        verifyNoInteractions(authorization);
    }

    @Test
    void revokedPermissionAfterTheInitialCheckPreventsMetadataFromBecomingNutrition() {
        Picture picture = new Picture();
        picture.setId(102L);
        picture.setIntroduction("不应被观察");
        when(pictures.findById(102L)).thenReturn(Optional.of(picture));
        org.mockito.Mockito.doThrow(new BusinessException(
                        com.li.lipicturecloud.exception.ErrorCode.NO_AUTH_ERROR, "缺少权限"))
                .when(authorization).checkForUser(PICTURE_VIEW, 102L, 7L);

        assertThatThrownBy(() -> provider.observe(authorized))
                .isInstanceOf(BusinessException.class)
                .hasMessage("缺少权限");
    }
}
