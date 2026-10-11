package com.li.lipicturecloud.application.companion.observation;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SafeLayoutPreviewTest {
    @Test void safeLayoutAcceptsOnlyGeometryAndNeverContainsUserTextOrImagePixels() throws Exception {
        var preview = SafeLayoutPreview.render("GALLERY", List.of(new ObservationContracts.Rect(10, 20, 100, 80)));
        var image = ImageIO.read(new ByteArrayInputStream(preview.bytes()));
        assertThat(image.getWidth()).isEqualTo(640); assertThat(image.getHeight()).isEqualTo(360);
        assertThat(preview.source()).isEqualTo("SAFE_LAYOUT"); assertThat(preview.digest()).hasSize(64);
        assertThat(preview.bytes()).isNotEmpty();
        byte[] copy=preview.bytes();copy[0]=0;assertThat(preview.bytes()[0]).isNotZero();
        assertThat(new String(preview.bytes(), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("password", "apiKey", "Authorization");
    }
    @Test void sensitiveUnknownOrEmptyRegionsFailClosedBeforeProducingPixels() {
        for(String page:List.of("MODEL_GATEWAY","PAYMENT","LOGIN","UNKNOWN"))
            assertThatThrownBy(()->SafeLayoutPreview.render(page,List.of(new ObservationContracts.Rect(0,0,20,20)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->SafeLayoutPreview.render("GALLERY",List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->SafeLayoutPreview.render("GALLERY",List.of(new ObservationContracts.Rect(-1,0,100,100)))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void unchangedGeometryHasStableDigestForCaching() {
        var rects=List.of(new ObservationContracts.Rect(10,20,100,80));
        assertThat(SafeLayoutPreview.render("GALLERY",rects).digest()).isEqualTo(SafeLayoutPreview.render("GALLERY",rects).digest());
        assertThat(SafeLayoutPreview.render("GALLERY",List.of(new ObservationContracts.Rect(12,20,100,80))).digest()).isNotEqualTo(SafeLayoutPreview.render("GALLERY",rects).digest());
    }
}
