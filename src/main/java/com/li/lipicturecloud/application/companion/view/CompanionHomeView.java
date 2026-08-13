package com.li.lipicturecloud.application.companion.view;

import java.util.List;

public record CompanionHomeView(CompanionView companion, NutritionStatusView nutrition,
                                List<GrowthRecordView> recentGrowth) {
}
