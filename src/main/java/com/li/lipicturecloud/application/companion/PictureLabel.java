package com.li.lipicturecloud.application.companion;

import java.util.List;
import java.util.Objects;

/**
 * 用户自己给图片写的名字、分类和标签。这是对方亲口提供的信息，可以直接用于回忆。
 */
public record PictureLabel(long pictureId, String name, String category, List<String> tags) {

    public PictureLabel {
        name = name == null ? "" : name.strip();
        category = category == null ? "" : category.strip();
        tags = List.copyOf(Objects.requireNonNullElse(tags, List.<String>of()));
    }

    /** 一句可读的说明，用于检索文本与提示词，例如 {@code 《骑士》 分类：游戏 标签：剑、少女}。 */
    public String describe() {
        StringBuilder text = new StringBuilder();
        if (!name.isEmpty()) {
            text.append('《').append(name).append('》');
        }
        if (!category.isEmpty()) {
            text.append(text.isEmpty() ? "" : " ").append("分类：").append(category);
        }
        if (!tags.isEmpty()) {
            text.append(text.isEmpty() ? "" : " ").append("标签：").append(String.join("、", tags));
        }
        return text.toString();
    }
}
