package com.li.lipicturecloud.domain.airuntime;

import com.li.lipicturecloud.application.airuntime.ImageGenerationResult;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageGenerationResultTest {

    @Test
    void acceptsUrlOrInlineImageResults() {
        ImageGenerationResult byUrl = new ImageGenerationResult(
                URI.create("https://cdn.example.test/result.png"), null);
        assertThat(byUrl.imageUrl()).isNotNull();
        assertThat(byUrl.base64Image()).isNull();

        ImageGenerationResult inline = new ImageGenerationResult(null, "aGVsbG8=");
        assertThat(inline.imageUrl()).isNull();
        assertThat(inline.base64Image()).isEqualTo("aGVsbG8=");
    }

    @Test
    void rejectsEmptyAndOversizedResults() {
        assertThatThrownBy(() -> new ImageGenerationResult(null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImageGenerationResult(null, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImageGenerationResult(null, "x".repeat(16 * 1024 * 1024 + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
