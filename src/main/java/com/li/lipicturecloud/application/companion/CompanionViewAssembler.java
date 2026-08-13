package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.companion.view.CompanionSkillView;
import com.li.lipicturecloud.application.companion.view.CompanionTraitsView;
import com.li.lipicturecloud.application.companion.view.CompanionView;
import com.li.lipicturecloud.application.companion.view.FeedPictureResult;
import com.li.lipicturecloud.application.companion.view.GrowthRecordView;
import com.li.lipicturecloud.application.companion.view.NutritionStatusView;
import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.domain.companion.CompanionSkill;
import com.li.lipicturecloud.domain.companion.CompanionTraits;
import com.li.lipicturecloud.domain.companion.GrowthEventType;
import com.li.lipicturecloud.domain.companion.GrowthRecord;
import com.li.lipicturecloud.domain.companion.NutritionMode;
import com.li.lipicturecloud.domain.companion.TraitDelta;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将领域对象转换成稳定的前端视图。
 * 规则名称、技能枚举和演示模式的披露都在这里收口，避免控制器泄漏持久化字段。
 */
@Component
public class CompanionViewAssembler {

    private final CompanionBalance balance;
    private final PictureNutritionAnalyzer analyzer;

    public CompanionViewAssembler(CompanionBalance balance, PictureNutritionAnalyzer analyzer) {
        this.balance = balance;
        this.analyzer = analyzer;
    }

    public CompanionView companion(Companion value) {
        List<CompanionSkillView> skills = Arrays.stream(CompanionSkill.values())
                .map(skill -> {
                    long experience = value.skillExperience().getOrDefault(skill, 0L);
                    return new CompanionSkillView(skill.name(), experience, balance.skillLevelFor(experience),
                            balance.nextSkillLevelExperience(experience));
                }).toList();
        return new CompanionView(value.id(), value.lifeExperience(), value.level(), value.lifeStage().name(),
                balance.totalExperienceForLevel(value.level()), balance.nextLevelExperience(value.lifeExperience()),
                traits(value.traits()), skills, value.balanceVersion(), value.revision());
    }

    public GrowthRecordView growth(GrowthRecord record) {
        Map<String, Long> skills = new LinkedHashMap<>();
        record.skillExperienceDelta().forEach((skill, delta) -> skills.put(skill.name(), delta));
        return new GrowthRecordView(record.id(), record.pictureId(), record.eventType().name(),
                record.lifeExperienceDelta(), traits(record.traitDelta()), Map.copyOf(skills), record.reason(),
                record.balanceVersion(), record.nutritionMode().name(), record.contentUnderstood(),
                record.createdTime());
    }

    public FeedPictureResult feedResult(GrowthRecord record) {
        String outcome = record.eventType() == GrowthEventType.PICTURE_FED ? "GROWN" : "FAMILIARITY";
        return new FeedPictureResult(outcome, record.correlationId(),
                companion(record.companionAfter()), growth(record));
    }

    public NutritionStatusView nutritionStatus() {
        // 图片分析的能力边界是产品事实，必须随主页响应返回，不能只写在开发文档里。
        return new NutritionStatusView(analyzer.mode().name(), analyzer.contentUnderstood(),
                nutritionNotice(analyzer.mode()));
    }

    private String nutritionNotice(NutritionMode mode) {
        return switch (mode) {
            case DEMO_DETERMINISTIC -> "仅根据图片 ID 选择固定营养档案，未读取图片内容，也未调用视觉模型。";
            case METADATA_DETERMINISTIC ->
                    "根据尺寸、格式、大小和图库文字状态生成基础营养；未读取图片像素，也未调用视觉模型。"
                            + "你填写的原文不会进入伙伴成长记录。";
        };
    }

    private CompanionTraitsView traits(CompanionTraits value) {
        return new CompanionTraitsView(value.curiosity(), value.enthusiasm(), value.playfulness(),
                value.empathy(), value.creativity());
    }

    private CompanionTraitsView traits(TraitDelta value) {
        return new CompanionTraitsView(value.curiosity(), value.enthusiasm(), value.playfulness(),
                value.empathy(), value.creativity());
    }
}
