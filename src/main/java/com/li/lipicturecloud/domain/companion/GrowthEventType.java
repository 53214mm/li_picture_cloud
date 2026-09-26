package com.li.lipicturecloud.domain.companion;

/** 成长记录的事件类型，用于区分首次完整喂养与重复图片熟悉度。 */
public enum GrowthEventType {
    /** 图片首次产生完整成长，可影响经验、性格和技能。 */
    PICTURE_FED,
    /** 图片已完整喂过，只产生受限的熟悉度经验。 */
    PICTURE_REVISITED
}
