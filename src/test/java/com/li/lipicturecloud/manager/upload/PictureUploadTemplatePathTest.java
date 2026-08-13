package com.li.lipicturecloud.manager.upload;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PictureUploadTemplatePathTest {

    @Test
    void generatedObjectKeyNeverStartsWithASlash() {
        assertThat(PictureUploadTemplate.objectKey("space/2087806139473432578", "picture.JPEG"))
                .isEqualTo("space/2087806139473432578/picture.JPEG");
    }
}
