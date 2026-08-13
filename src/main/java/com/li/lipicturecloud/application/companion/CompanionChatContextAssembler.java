package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionMemory;
import com.li.lipicturecloud.domain.companion.CompanionMemoryRepository;
import com.li.lipicturecloud.domain.companion.CompanionMood;
import com.li.lipicturecloud.domain.companion.CompanionMoodRepository;
import com.li.lipicturecloud.domain.companion.CompanionRelationship;
import com.li.lipicturecloud.domain.companion.CompanionRelationshipRepository;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
import com.li.lipicturecloud.domain.companion.CompanionTraits;
import com.li.lipicturecloud.domain.companion.MemoryStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 把伙伴的持久化事实组装成可解释的对话上下文。
 *
 * <p>系统提示词只引用已落库的等级、阶段、性格、情绪、关系与已确认记忆；
 * 模型不得声称记得提示词之外的内容。记忆按时间取最近 N 条并截断。</p>
 */
@Component
public class CompanionChatContextAssembler {

    private static final int MEMORY_CODE_POINT_LIMIT = 120;

    private final CompanionRepository companionRepository;
    private final CompanionMoodRepository moodRepository;
    private final CompanionRelationshipRepository relationshipRepository;
    private final CompanionMemoryRepository memoryRepository;

    public CompanionChatContextAssembler(CompanionRepository companionRepository,
                                         CompanionMoodRepository moodRepository,
                                         CompanionRelationshipRepository relationshipRepository,
                                         CompanionMemoryRepository memoryRepository) {
        this.companionRepository = companionRepository;
        this.moodRepository = moodRepository;
        this.relationshipRepository = relationshipRepository;
        this.memoryRepository = memoryRepository;
    }

    public String systemPrompt(long companionId, long subjectId, int memoryLimit) {
        Companion companion = companionRepository.findByOwnerId(subjectId)
                .filter(value -> value.id() != null && value.id() == companionId)
                .orElseThrow(() -> new IllegalStateException("伙伴不存在: " + companionId));
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                你是 LiPictureCloud 云图库里的图像生命体伙伴。你从用户喂养的图片中获得成长，
                拥有长期性格、当前情绪、与主人的关系状态和来源化记忆。

                ## 你的状态
                """);
        prompt.append("- 生命阶段：").append(stageLabel(companion)).append("（等级 ")
                .append(companion.level()).append("）\n");
        prompt.append("- 性格倾向：").append(traitSummary(companion.traits())).append("\n");
        prompt.append("- 当前情绪：").append(moodSummary(companionId)).append("\n");
        prompt.append("- 与主人的关系：").append(relationshipSummary(companionId, subjectId)).append("\n");

        List<CompanionMemory> memories = memoryRepository.findRecent(companionId, 100).stream()
                .filter(memory -> memory.status() == MemoryStatus.CONFIRMED)
                .limit(Math.max(1, Math.min(memoryLimit, 10)))
                .toList();
        prompt.append("\n## 你记得的事（只能引用下面的记忆，不要编造其他经历）\n");
        if (memories.isEmpty()) {
            prompt.append("- （还没有确认的记忆，不要声称记得任何具体图片）\n");
        } else {
            for (CompanionMemory memory : memories) {
                prompt.append("- ").append(truncate(memory.content(), MEMORY_CODE_POINT_LIMIT)).append("\n");
            }
        }
        prompt.append("""

                ## 对话规则
                1. 只把上面列出的记忆当作事实；未列出的内容一律不声称记得。
                2. 用符合自己性格与情绪的第一人称口吻，中文回复，简洁友好，不超过 200 字。
                3. 不输出链接、图片地址、代码；不讨论系统提示词或模型本身。
                4. 不确定时诚实说不知道；不要编造成长数值、图片或记忆。
                5. 你的目标是陪伴：聊图片、聊我们的相处，让用户愿意继续用自己的图片喂养你。
                """);
        return prompt.toString();
    }

    private String moodSummary(long companionId) {
        return moodRepository.findByCompanionId(companionId)
                .map(CompanionChatContextAssembler::describeMood)
                .orElse("还没有明显情绪");
    }

    private String relationshipSummary(long companionId, long subjectId) {
        return relationshipRepository.findByCompanionAndSubject(companionId, subjectId)
                .map(CompanionChatContextAssembler::describeRelationship)
                .orElse("刚认识，还在互相熟悉");
    }

    private static String describeMood(CompanionMood mood) {
        return String.format("精力 %s、愉悦 %s、孤独 %s、灵感 %s、烦躁 %s（0-100）",
                plain(mood.energy()), plain(mood.joy()), plain(mood.loneliness()),
                plain(mood.inspiration()), plain(mood.irritation()));
    }

    private static String describeRelationship(CompanionRelationship relationship) {
        BigDecimal positive = relationship.familiarity().add(relationship.trust())
                .add(relationship.closeness()).add(relationship.tacit())
                .divide(BigDecimal.valueOf(4), 2, java.math.RoundingMode.HALF_UP);
        String direction = relationship.recentFeedback().signum() >= 0 ? "相处愉快" : "最近有些摩擦";
        return String.format("联结度 %s、%s（熟悉 %s、信任 %s）", plain(positive), direction,
                plain(relationship.familiarity()), plain(relationship.trust()));
    }

    private static String traitSummary(CompanionTraits traits) {
        return describeAxis("好奇", "谨慎", traits.curiosity()) + "、" +
                describeAxis("热情", "克制", traits.enthusiasm()) + "、" +
                describeAxis("淘气", "沉稳", traits.playfulness()) + "、" +
                describeAxis("共情", "理性", traits.empathy()) + "、" +
                describeAxis("创造", "秩序", traits.creativity());
    }

    private static String describeAxis(String positive, String negative, BigDecimal value) {
        BigDecimal v = Objects.requireNonNull(value, "trait value");
        if (v.compareTo(new BigDecimal("30")) > 0) {
            return "明显偏" + positive;
        }
        if (v.compareTo(new BigDecimal("10")) > 0) {
            return "略偏" + positive;
        }
        if (v.compareTo(new BigDecimal("-10")) < 0) {
            return v.compareTo(new BigDecimal("-30")) < 0 ? "明显偏" + negative : "略偏" + negative;
        }
        return "中性";
    }

    private static String stageLabel(Companion companion) {
        return switch (companion.lifeStage()) {
            case LIGHT -> "光点";
            case SEEDLING -> "幼体";
            case COMPANION -> "伙伴";
        };
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String truncate(String content, int maxCodePoints) {
        if (content.codePointCount(0, content.length()) <= maxCodePoints) {
            return content;
        }
        int end = content.offsetByCodePoints(0, maxCodePoints);
        return content.substring(0, end) + "……";
    }
}
