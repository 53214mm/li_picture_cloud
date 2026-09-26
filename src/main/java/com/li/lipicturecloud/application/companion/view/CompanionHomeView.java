package com.li.lipicturecloud.application.companion.view;

import java.util.List;

/**
 * 伙伴主页的聚合响应：当前伙伴快照、营养能力披露以及最近成长时间线。
 *
 * <p>这是面向前端的只读 View，不是数据库 Entity，也不承载成长规则。</p>
 */
public record CompanionHomeView(CompanionView companion, NutritionStatusView nutrition,
                                List<GrowthRecordView> recentGrowth,
                                CompanionMoodView mood, CompanionRelationshipView relationship,
                                String chatPolicy) {

    /** 兼容构造器：尚未有情绪/关系/对话策略数据的旧调用点保持可用。 */
    public CompanionHomeView(CompanionView companion, NutritionStatusView nutrition,
                             List<GrowthRecordView> recentGrowth) {
        this(companion, nutrition, recentGrowth, null, null, null);
    }
}
