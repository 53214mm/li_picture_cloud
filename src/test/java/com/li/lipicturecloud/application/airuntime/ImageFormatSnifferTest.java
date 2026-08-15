package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CreationFusionImage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageFormatSnifferTest {

    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 1, 2, 3, 4, 5, 6, 7};
    private static final byte[] WEBP = {
            'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P'};

    @Test
    void detectsSupportedFormatsByMagicBytes() {
        assertThat(ImageFormatSniffer.detect(PNG)).isEqualTo("image/png");
        assertThat(ImageFormatSniffer.detect(JPEG)).isEqualTo("image/jpeg");
        assertThat(ImageFormatSniffer.detect(WEBP)).isEqualTo("image/webp");
    }

    @Test
    void rejectsUnknownTooShortAndOversizedInput() {
        assertThatThrownBy(() -> ImageFormatSniffer.detect(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImageFormatSniffer.detect(new byte[]{1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImageFormatSniffer.detect(new byte[24]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImageFormatSniffer.detect(
                new byte[(int) CreationFusionImage.MAX_BYTES + 1]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
