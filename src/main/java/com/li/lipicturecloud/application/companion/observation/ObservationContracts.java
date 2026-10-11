package com.li.lipicturecloud.application.companion.observation;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** No arbitrary browser strings or pixels enter the observation boundary. */
public final class ObservationContracts {
    private ObservationContracts() {}
    public static final int TEXT_MAX = 500, VISION_MAX = 100;
    public static final int TEXT_DAY_MAX = 1000, VISION_DAY_MAX = 200;
    public static final Set<String> PAGES = Set.of("GALLERY", "PICTURE_DETAIL", "COMPANION", "UPLOAD");
    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (x < 0 || y < 0 || width < 1 || height < 1 || x + (long) width > 640 || y + (long) height > 360)
                throw new IllegalArgumentException("UNSAFE_GEOMETRY");
        }
    }
    public record PageInput(String page, List<Long> pictureIds, List<Rect> rects) {
        public PageInput {
            pictureIds = pictureIds == null ? List.of() : List.copyOf(pictureIds);
            rects = rects == null ? List.of() : List.copyOf(rects);
            if (pictureIds.size() > 6 || rects.size() > 6 || pictureIds.stream().anyMatch(id -> id == null || id <= 0))
                throw new IllegalArgumentException("INVALID_VISIBLE_REFS");
        }
    }
    public record OpenInput(int textLimit, int visionLimit, String intensity, boolean auto,
                            String textReceiverToken, String visionReceiverToken) {
        public OpenInput(int textLimit,int visionLimit,String intensity,boolean auto) { this(textLimit,visionLimit,intensity,auto,null,null); }
        public OpenInput {
            if (textLimit < 1 || textLimit > TEXT_MAX || visionLimit < 0 || visionLimit > VISION_MAX)
                throw new IllegalArgumentException("BUDGET_OUT_OF_RANGE");
            if (!Set.of("LOW", "NORMAL").contains(intensity)) throw new IllegalArgumentException("INVALID_INTENSITY");
        }
    }
    public record PictureRef(long id, String name, String category, String tags, Instant updatedAt) {}
    public record ChatLine(long id, String role, String content, Instant at) {}
    public record MemoryLine(long id, Long pictureId, String content, Instant at) {}
    public record Snapshot(String page, List<PictureRef> pictures, List<ChatLine> chat,
                           List<MemoryLine> memories, long chatEpoch, Instant capturedAt) {
        public Snapshot { pictures=List.copyOf(pictures);chat=List.copyOf(chat);memories=List.copyOf(memories); }
    }
}
