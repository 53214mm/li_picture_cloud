package com.li.lipicturecloud.infrastructure.companion;

import com.li.lipicturecloud.application.companion.AuthorizedPictureContent;
import com.li.lipicturecloud.application.companion.AuthorizedPictureRef;
import com.li.lipicturecloud.application.companion.VisionContentException;
import com.li.lipicturecloud.config.CosClientConfig;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.manager.CosManager;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import com.li.lipicturecloud.model.entity.Picture;
import com.li.lipicturecloud.repository.PictureRepository;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import org.junit.jupiter.api.Test;
import org.apache.http.client.methods.HttpRequestBase;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant.PICTURE_VIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 视觉模型外发前的最后一道图片边界：对象地址、权限和版本三者都必须在下载前后保持一致。
 */
class CosAuthorizedPictureContentProviderTest {

    private static final long PICTURE_ID = 102L;
    private static final long SUBJECT_ID = 7L;

    private final PictureRepository pictures = mock(PictureRepository.class);
    private final SpaceAuthorizationAccessService authorization = mock(SpaceAuthorizationAccessService.class);
    private final CosManager cos = mock(CosManager.class);
    private final CosAuthorizedPictureContentProvider provider = new CosAuthorizedPictureContentProvider(
            pictures, authorization, cos, cosConfiguration());
    private final AuthorizedPictureRef reference =
            new AuthorizedPictureRef(AuthorizationSubject.user(SUBJECT_ID), PICTURE_ID);

    @Test
    void rejectsForeignHostWithoutMakingAnyCosCall() {
        Picture foreign = picture("https://assets.example.test.evil.invalid/gallery/photo.jpg", Instant.EPOCH);
        when(pictures.findById(PICTURE_ID)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> provider.load(reference, 32L))
                .isInstanceOf(VisionContentException.class)
                .extracting(error -> ((VisionContentException) error).safeCode())
                .isEqualTo("VISION_IMAGE_UNAVAILABLE");

        verify(authorization).checkForUser(PICTURE_VIEW, PICTURE_ID, SUBJECT_ID);
        verifyNoInteractions(cos);
    }

    @Test
    void fallsBackToAnAuthorizedDerivedObjectWhenTheLegacyOriginalUrlIsMalformed() {
        Picture picture = picture("https://assets.example.test//gallery/original.jpg", Instant.EPOCH);
        picture.setUrl("https://assets.example.test/gallery/derived.webp");
        when(pictures.findById(PICTURE_ID)).thenReturn(Optional.of(picture), Optional.of(picture));
        when(cos.getObject("gallery/derived.webp")).thenReturn(cosObject(webpBytes()));

        AuthorizedPictureContent content = provider.load(reference, 32L);

        assertThat(content.mimeType()).isEqualTo("image/webp");
        assertThat(content.bytes()).containsExactly(webpBytes());
        verify(cos).getObject("gallery/derived.webp");
    }

    @Test
    void rechecksPermissionAndVersionAfterDownloadBeforeReturningBytes() {
        Picture before = picture("https://assets.example.test/gallery/photo.jpg", Instant.EPOCH);
        Picture moved = picture("https://assets.example.test/gallery/photo.jpg", Instant.EPOCH.plusSeconds(1));
        when(pictures.findById(PICTURE_ID)).thenReturn(Optional.of(before), Optional.of(moved));
        when(cos.getObject("gallery/photo.jpg")).thenReturn(cosObject(jpegBytes()));

        assertThatThrownBy(() -> provider.load(reference, 32L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("图片状态已变化或无权访问");

        verify(authorization, times(2)).checkForUser(PICTURE_VIEW, PICTURE_ID, SUBJECT_ID);
        verify(cos).getObject("gallery/photo.jpg");
    }

    @Test
    void recheckedRevokedPermissionPreventsReturningAlreadyDownloadedBytes() {
        Picture picture = picture("https://assets.example.test/gallery/photo.jpg", Instant.EPOCH);
        when(pictures.findById(PICTURE_ID)).thenReturn(Optional.of(picture), Optional.of(picture));
        when(cos.getObject("gallery/photo.jpg")).thenReturn(cosObject(jpegBytes()));
        doNothing().doThrow(new BusinessException(40101, "缺少权限"))
                .when(authorization).checkForUser(PICTURE_VIEW, PICTURE_ID, SUBJECT_ID);

        assertThatThrownBy(() -> provider.load(reference, 32L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("缺少权限");
    }

    @Test
    void finalOutboundCheckRejectsAnObjectAddressChangeEvenWhenVersionWasNotUpdated() {
        Picture original = picture("https://assets.example.test/gallery/photo.jpg", Instant.EPOCH);
        Picture replaced = picture("https://assets.example.test/gallery/replaced.jpg", Instant.EPOCH);
        when(pictures.findById(PICTURE_ID)).thenReturn(Optional.of(original), Optional.of(original),
                Optional.of(replaced));
        when(cos.getObject("gallery/photo.jpg")).thenReturn(cosObject(jpegBytes()));

        AuthorizedPictureContent content = provider.load(reference, 32L);

        assertThatThrownBy(() -> provider.verifyStillAuthorized(reference, content))
                .isInstanceOf(BusinessException.class)
                .hasMessage("图片状态已变化或无权访问");
        verify(authorization, times(3)).checkForUser(PICTURE_VIEW, PICTURE_ID, SUBJECT_ID);
    }

    @Test
    void stopsAtTheByteLimitInsteadOfReadingAnUnboundedObject() {
        Picture picture = picture("https://assets.example.test/gallery/photo.png", Instant.EPOCH);
        when(pictures.findById(PICTURE_ID)).thenReturn(Optional.of(picture));
        when(cos.getObject("gallery/photo.png")).thenReturn(cosObject(new byte[]{1, 2, 3, 4, 5}));

        assertThatThrownBy(() -> provider.load(reference, 4L))
                .isInstanceOf(VisionContentException.class)
                .extracting(error -> ((VisionContentException) error).safeCode())
                .isEqualTo("VISION_IMAGE_TOO_LARGE");
    }

    @Test
    void rejectsContentWhoseBytesDoNotMatchTheAllowedImageMimeType() {
        Picture picture = picture("https://assets.example.test/gallery/photo.jpg", Instant.EPOCH);
        when(pictures.findById(PICTURE_ID)).thenReturn(Optional.of(picture));
        when(cos.getObject("gallery/photo.jpg")).thenReturn(cosObject(new byte[]{1, 2, 3}));

        assertThatThrownBy(() -> provider.load(reference, 32L))
                .isInstanceOf(VisionContentException.class)
                .extracting(error -> ((VisionContentException) error).safeCode())
                .isEqualTo("VISION_UNSUPPORTED_IMAGE_FORMAT");
    }

    @Test
    void returnedBytesCannotMutateTheStoredSnapshot() {
        Picture picture = picture("https://assets.example.test/gallery/photo.webp", Instant.EPOCH);
        when(pictures.findById(PICTURE_ID)).thenReturn(Optional.of(picture), Optional.of(picture));
        byte[] expected = webpBytes();
        when(cos.getObject("gallery/photo.webp")).thenReturn(cosObject(expected));

        AuthorizedPictureContent content = provider.load(reference, 32L);
        byte[] callerCopy = content.bytes();
        callerCopy[0] = 99;

        assertThat(content.mimeType()).isEqualTo("image/webp");
        assertThat(content.bytes()).containsExactly(expected);
        assertThat(Arrays.equals(callerCopy, content.bytes())).isFalse();
    }

    @Test
    void closesBothTheCosObjectAndItsInputStream() throws Exception {
        Picture picture = picture("https://assets.example.test/gallery/photo.jpg", Instant.EPOCH);
        when(pictures.findById(PICTURE_ID)).thenReturn(Optional.of(picture), Optional.of(picture));
        COSObject object = mock(COSObject.class);
        COSObjectInputStream stream = mock(COSObjectInputStream.class);
        when(cos.getObject("gallery/photo.jpg")).thenReturn(object);
        when(object.getObjectContent()).thenReturn(stream);
        AtomicInteger reads = new AtomicInteger();
        doAnswer(invocation -> {
            if (reads.getAndIncrement() > 0) {
                return -1;
            }
            byte[] target = invocation.getArgument(0);
            byte[] jpeg = jpegBytes();
            System.arraycopy(jpeg, 0, target, 0, jpeg.length);
            return jpeg.length;
        }).when(stream).read(any(byte[].class), anyInt(), anyInt());

        provider.load(reference, 32L);

        verify(stream).close();
        verify(object).close();
    }

    private static CosClientConfig cosConfiguration() {
        CosClientConfig configuration = new CosClientConfig();
        configuration.setHost("https://assets.example.test");
        return configuration;
    }

    private static Picture picture(String url, Instant updateTime) {
        Picture picture = new Picture();
        picture.setId(PICTURE_ID);
        picture.setOriginalUrl(url);
        picture.setUpdateTime(Date.from(updateTime));
        return picture;
    }

    private static COSObject cosObject(byte[] bytes) {
        COSObject object = new COSObject();
        object.setObjectContent(new COSObjectInputStream(
                new ByteArrayInputStream(bytes), mock(HttpRequestBase.class)));
        return object;
    }

    private static byte[] jpegBytes() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    }

    private static byte[] webpBytes() {
        return new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
    }
}
