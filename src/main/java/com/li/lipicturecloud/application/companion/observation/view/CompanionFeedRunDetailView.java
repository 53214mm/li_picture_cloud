package com.li.lipicturecloud.application.companion.observation.view;

import com.li.lipicturecloud.application.companion.view.GrowthRecordView;

import java.util.List;

/** 单次喂养观测详情。 */
public record CompanionFeedRunDetailView(
        CompanionFeedRunSummaryView summary,
        List<CompanionFeedStageView> timeline,
        GrowthRecordView growth,
        CompanionFeedTechnicalView technical,
        String safeErrorMessage) {
}
