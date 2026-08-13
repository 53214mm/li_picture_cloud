package com.li.lipicturecloud.application.companion.view;

import java.util.List;

public record CompanionHomeView(CompanionView companion, NutritionStatusView nutrition,
                                List<GrowthRecordView> recentGrowth,
                                CompanionMoodView mood, CompanionRelationshipView relationship) {

    /** 兼容构造器：尚未有情绪/关系数据的旧调用点保持可用。 */
    public CompanionHomeView(CompanionView companion, NutritionStatusView nutrition,
                             List<GrowthRecordView> recentGrowth) {
        this(companion, nutrition, recentGrowth, null, null);
    }
}
