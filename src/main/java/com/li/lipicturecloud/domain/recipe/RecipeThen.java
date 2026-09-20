package com.li.lipicturecloud.domain.recipe;

import com.li.lipicturecloud.domain.airuntime.CreationKind;

import java.util.Objects;
import java.util.Set;

/**
 * 配方 THEN：单一动作，只能是白名单能力（三种创作玩法）之一。
 * 目标空间与可见性不在配方里预先决定——保存前仍由用户确认、后端校验（收紧不扩大）。
 */
public record RecipeThen(CreationKind capability) {

    private static final Set<CreationKind> ALLOWED = Set.of(
            CreationKind.STORY_DRAFT, CreationKind.EMOJI_DRAFT, CreationKind.IMAGE_FUSION);

    public RecipeThen {
        Objects.requireNonNull(capability, "capability");
        if (!ALLOWED.contains(capability)) {
            throw new IllegalArgumentException("capability is not on the recipe whitelist: "
                    + capability);
        }
    }
}
