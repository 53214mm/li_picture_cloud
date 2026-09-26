package com.li.lipicturecloud.domain.companion;

/** 伙伴可积累熟练度的封闭技能集合；枚举值同时作为持久化 skillCode。 */
public enum CompanionSkill {
    /** 观察并理解图片。 */
    IMAGE_OBSERVATION,
    /** 根据图片或上下文创作故事。 */
    STORY_CREATION,
    /** 生成表情或回复候选。 */
    EMOJI_CREATION,
    /** 融合多张授权图片。 */
    IMAGE_FUSION,
    /** 在授权图库中检索素材。 */
    GALLERY_SEARCH
}
