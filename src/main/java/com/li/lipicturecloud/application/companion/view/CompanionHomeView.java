package com.li.lipicturecloud.application.companion.view;

import java.util.List;

/**
 * 伙伴主页的聚合响应：当前伙伴快照、营养能力披露以及最近成长时间线。
 *
 * <p>这是面向前端的只读 View，不是数据库 Entity，也不承载成长规则。</p>
 *
 * <p>{@code chatPolicy} 保持为服务端配置的对话策略（DEMO_ONLY / MODEL，测试据此确认不会外发）；
 * 本用户实际生效的模式、可用性与模型来源在 {@code chatMode} 中。</p>
 */
public record CompanionHomeView(CompanionView companion, NutritionStatusView nutrition,
                                List<GrowthRecordView> recentGrowth,
                                CompanionMoodView mood, CompanionRelationshipView relationship,
                                String chatPolicy, CompanionChatModeView chatMode) {

    /** 兼容构造器：尚未有情绪/关系/对话策略数据的旧调用点保持可用。 */
    public CompanionHomeView(CompanionView companion, NutritionStatusView nutrition,
                             List<GrowthRecordView> recentGrowth) {
        this(companion, nutrition, recentGrowth, null, null, null, null);
    }

    /** 兼容构造器：聊天模式由控制层按当前用户补齐。 */
    public CompanionHomeView(CompanionView companion, NutritionStatusView nutrition,
                             List<GrowthRecordView> recentGrowth,
                             CompanionMoodView mood, CompanionRelationshipView relationship,
                             String chatPolicy) {
        this(companion, nutrition, recentGrowth, mood, relationship, chatPolicy, null);
    }

    /** 补上当前用户的聊天模式；服务端策略字段不变。 */
    public CompanionHomeView withChatMode(CompanionChatModeView chatMode) {
        return new CompanionHomeView(companion, nutrition, recentGrowth, mood, relationship,
                chatPolicy, chatMode);
    }
}
