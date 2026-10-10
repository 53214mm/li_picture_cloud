package com.li.lipicturecloud.infrastructure.companion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.companion.PictureLabel;
import com.li.lipicturecloud.application.companion.PictureLabelProvider;
import com.li.lipicturecloud.model.entity.Picture;
import com.li.lipicturecloud.repository.PictureRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从图片记录读取用户写的名字、分类与标签；读不到的图片直接跳过。
 */
@Component
public class RepositoryPictureLabelProvider implements PictureLabelProvider {

    private static final int MAX_TAGS = 8;

    private final PictureRepository pictures;
    private final ObjectMapper objectMapper;

    public RepositoryPictureLabelProvider(PictureRepository pictures, ObjectMapper objectMapper) {
        this.pictures = pictures;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<Long, PictureLabel> labels(Collection<Long> pictureIds) {
        Map<Long, PictureLabel> labels = new LinkedHashMap<>();
        for (Long id : pictureIds) {
            if (id == null || id <= 0 || labels.containsKey(id)) {
                continue;
            }
            pictures.findById(id).ifPresent(picture -> labels.put(id, label(picture)));
        }
        return labels;
    }

    private PictureLabel label(Picture picture) {
        return new PictureLabel(picture.getId(), picture.getName(), picture.getCategory(), tags(picture.getTags()));
    }

    /** Tags are stored as a JSON array string; anything else is ignored rather than guessed. */
    private List<String> tags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<String>>() { }).stream()
                    .filter(tag -> tag != null && !tag.isBlank())
                    .map(String::strip)
                    .limit(MAX_TAGS)
                    .toList();
        } catch (Exception malformed) {
            return List.of();
        }
    }
}
