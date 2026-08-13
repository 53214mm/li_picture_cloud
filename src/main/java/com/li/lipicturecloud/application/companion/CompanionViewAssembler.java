package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.companion.view.CompanionMemoryView;
import com.li.lipicturecloud.application.companion.view.CompanionMoodView;
import com.li.lipicturecloud.application.companion.view.CompanionRelationshipView;
import com.li.lipicturecloud.application.companion.view.CompanionSkillView;
import com.li.lipicturecloud.application.companion.view.CompanionTraitsView;
import com.li.lipicturecloud.application.companion.view.CompanionView;
import com.li.lipicturecloud.application.companion.view.FeedPictureResult;
import com.li.lipicturecloud.application.companion.view.GrowthRecordView;
import com.li.lipicturecloud.application.companion.view.NutritionStatusView;
import com.li.lipicturecloud.config.CompanionFeatureProperties;
import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.domain.companion.CompanionMemory;
import com.li.lipicturecloud.domain.companion.CompanionMood;
import com.li.lipicturecloud.domain.companion.CompanionRelationship;
import com.li.lipicturecloud.domain.companion.CompanionSkill;
import com.li.lipicturecloud.domain.companion.CompanionTraits;
import com.li.lipicturecloud.domain.companion.GrowthEventType;
import com.li.lipicturecloud.domain.companion.GrowthRecord;
import com.li.lipicturecloud.domain.companion.NutritionMode;
import com.li.lipicturecloud.domain.companion.NutritionPolicy;
import com.li.lipicturecloud.domain.companion.NutritionProvenance;
import com.li.lipicturecloud.domain.companion.TraitDelta;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
    private final CompanionFeatureProperties properties;

    public CompanionViewAssembler(CompanionBalance balance, PictureNutritionAnalyzer analyzer,
                                  CompanionFeatureProperties properties) {
        this.balance = balance;
        this.analyzer = analyzer;
        this.properties = properties;
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

    public CompanionMoodView mood(CompanionMood value) {
        return new CompanionMoodView(value.energy(), value.joy(), value.loneliness(), value.inspiration(),
                value.irritation(), moodSummary(value), value.updatedAt());
    }

    public CompanionRelationshipView relationship(CompanionRelationship value) {
        return new CompanionRelationshipView(value.familiarity(), value.trust(), value.closeness(),
                value.tacit(), value.recentFeedback());
    }

    public CompanionMemoryView memory(CompanionMemory memory) {
        return new CompanionMemoryView(memory.id(), memory.sourceType().name(),
                memory.exposesContent() ? memory.content() : null,
                memory.exposesContent() ? memory.originalContent() : null,
                memory.confidence(), memory.status().name(), memory.invalidatedReason(),
                memory.pictureId(), memory.createdTime(), memory.updatedTime());
    }

    public GrowthRecordView growth(GrowthRecord record) {
        Map<String, Long> skills = new LinkedHashMap<>();
        record.skillExperienceDelta().forEach((skill, delta) -> skills.put(skill.name(), delta));
        return new GrowthRecordView(record.id(), record.pictureId(), record.eventType().name(),
                record.lifeExperienceDelta(), traits(record.traitDelta()), Map.copyOf(skills), record.reason(),
                record.balanceVersion(), record.provenance().actualMode().name(),
                record.provenance().contentUnderstood(),
                record.provenance().providerCode(), record.provenance().modelCode(),
                record.provenance().confidence(), record.provenance().fallbackReasonCode(),
                nutritionLabel(record.provenance()),
                record.createdTime());
    }

    public FeedPictureResult feedResult(GrowthRecord record) {
        String outcome = record.eventType() == GrowthEventType.PICTURE_FED ? "GROWN" : "FAMILIARITY";
        return new FeedPictureResult(outcome, record.correlationId(),
                companion(record.companionAfter()), growth(record));
    }

    public NutritionStatusView nutritionStatus() {
        // 能力边界是产品事实，必须随主页响应返回，不能只写在开发文档里。
        NutritionPolicy policy = analyzer.policy();
        return switch (policy) {
            case DEMO_ONLY -> new NutritionStatusView(policy.name(), "internal", "demo-v1", 0,
                    nutritionNotice(policy));
            case METADATA_ONLY -> new NutritionStatusView(policy.name(), "internal", "metadata-v1", 0,
                    nutritionNotice(policy));
            case VISUAL_WITH_METADATA_FALLBACK -> new NutritionStatusView(policy.name(),
                    properties.getVisionProvider(), properties.getVisionModel(), properties.getVisionDailyLimit(),
                    nutritionNotice(policy));
        };
    }

    private String moodSummary(CompanionMood mood) {
        BigDecimal threshold = new BigDecimal("5.00");
        BigDecimal dominant = mood.energy().max(mood.joy()).max(mood.loneliness())
                .max(mood.inspiration()).max(mood.irritation());
        if (dominant.compareTo(threshold) < 0) {
            return "它此刻很平静，正等着和你一起看看图片。";
        }
        // 并列时按精力、愉悦、孤独、灵感、烦躁的优先级选择一条稳定摘要。
        if (mood.energy().compareTo(dominant) == 0) {
            return "它此刻精力充沛，想和你多待一会儿。";
        }
        if (mood.joy().compareTo(dominant) == 0) {
            return "它此刻心情明亮，被愉快的感觉包围。";
        }
        if (mood.loneliness().compareTo(dominant) == 0) {
            return "它此刻有点孤单，想要你陪它看看图。";
        }
        if (mood.inspiration().compareTo(dominant) == 0) {
            return "它此刻灵感涌动，脑子里冒出了新想法。";
        }
        return "它此刻有点烦躁，需要安静一会儿。";
    }

    private String nutritionNotice(NutritionPolicy policy) {
        return switch (policy) {
            case DEMO_ONLY -> "仅根据图片 ID 选择固定营养档案，未读取图片内容，也未调用视觉模型。";
            case METADATA_ONLY ->
                    "根据尺寸、格式、大小和图库文字状态生成基础营养；未读取图片像素，也未调用视觉模型。"
                            + "你填写的原文不会进入伙伴成长记录。";
            case VISUAL_WITH_METADATA_FALLBACK -> "喂养时会向已配置的视觉模型发送已授权图片像素；每天最多 "
                    + properties.getVisionDailyLimit() + " 次。若服务暂不可用，成长记录会明确标注元数据降级。";
        };
    }

    private String nutritionLabel(NutritionProvenance provenance) {
        if (provenance.actualMode() == NutritionMode.VISUAL_MODEL) {
            return visualProviderLabel(provenance.providerCode()) + " 视觉营养 · 已分析图片内容";
        }
        if (provenance.fallbackReasonCode() != null) {
            return "视觉服务暂不可用，本次使用图片元数据营养";
        }
        return provenance.actualMode() == NutritionMode.DEMO_DETERMINISTIC
                ? "演示营养（未读取图片内容）"
                : "图片元数据营养（未读取图片像素）";
    }

    private String visualProviderLabel(String providerCode) {
        return "dashscope".equalsIgnoreCase(providerCode) ? "Qwen" : "视觉模型";
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
