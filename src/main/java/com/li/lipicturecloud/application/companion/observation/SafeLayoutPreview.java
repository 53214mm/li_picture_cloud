package com.li.lipicturecloud.application.companion.observation;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Creates pixels only after excluding all user text/photos/inputs; never captures a screen. */
public final class SafeLayoutPreview {
    private SafeLayoutPreview() {}
    public static Preview render(String page, List<ObservationContracts.Rect> rects) {
        if (!ObservationContracts.PAGES.contains(page) || rects == null || rects.isEmpty() || rects.size() > 6)
            throw new IllegalArgumentException("NO_SAFE_LAYOUT");
        var image = new BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(242, 245, 249)); graphics.fillRect(0, 0, 640, 360);
            for (var rect : rects) {
                graphics.setColor(new Color(207, 217, 230)); graphics.fillRect(rect.x(), rect.y(), rect.width(), rect.height());
                graphics.setColor(new Color(115, 131, 151)); graphics.drawRect(rect.x(), rect.y(), rect.width()-1, rect.height()-1);
            }
        } finally { graphics.dispose(); }
        try {
            var buffer = new ByteArrayOutputStream(); ImageIO.write(image, "png", buffer);
            byte[] bytes = buffer.toByteArray();
            return new Preview(bytes, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), "SAFE_LAYOUT");
        } catch (IOException | NoSuchAlgorithmException impossible) { throw new IllegalStateException("PREVIEW_UNAVAILABLE", impossible); }
    }
    public record Preview(byte[] bytes, String digest, String source) {
        public Preview { bytes=bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
}
