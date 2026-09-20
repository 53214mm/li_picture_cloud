package com.li.lipicturecloud.application.companion.observation.view;

import java.time.Instant;

/** 后台展示的一段喂养阶段说明。中间阶段没有单独落时间时，occurredAt 为 null。 */
public record CompanionFeedStageView(
        String code,
        String status,
        String label,
        String description,
        Instant occurredAt) {
}
