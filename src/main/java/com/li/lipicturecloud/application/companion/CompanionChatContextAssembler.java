package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionMemory;
import com.li.lipicturecloud.domain.companion.CompanionMood;
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
 * <p>系统提示词由绫页人格设定加上粗粒度的状态描述组成：成长、心情、熟悉程度只用来调语气，
 * 不给数值、不用内部阶段名。另外引用调用方注入的"当前情绪"与
 * "可用已确认记忆"；模型不得声称记得提示词之外的内容。情绪与记忆不在本组件内查询：
 * 调用方负责先完成情绪惰性衰减与来源图片撤权检查（fail-closed），本组件只负责截断、
 * 截尾与格式化，避免衍生使用路径绕过授权守卫。</p>
 */
@Component
public class CompanionChatContextAssembler {

    private static final int MEMORY_CODE_POINT_LIMIT = 120;
    private static final BigDecimal STRONG_MOOD = new BigDecimal("60");
    private static final BigDecimal STRONG_TRAIT = new BigDecimal("30");
    private static final java.time.ZoneId SHANGHAI = java.time.ZoneId.of("Asia/Shanghai");
    private static final java.time.format.DateTimeFormatter MEMORY_DATE =
            java.time.format.DateTimeFormatter.ofPattern("M月d日");

    private final CompanionRepository companionRepository;
    private final CompanionRelationshipRepository relationshipRepository;

    public CompanionChatContextAssembler(CompanionRepository companionRepository,
                                         CompanionRelationshipRepository relationshipRepository) {
        this.companionRepository = companionRepository;
        this.relationshipRepository = relationshipRepository;
    }

    /**
     * 组装系统提示词。
     *
     * @param currentMood    已按完整小时衰减过的当前情绪；无情绪行时为 {@code null}
     * @param usableMemories 已通过来源图片撤权检查和本轮相关筛选的已确认记忆（最近优先）
     */
    public String systemPrompt(long companionId, long subjectId, int memoryLimit,
                               CompanionMood currentMood, List<CompanionMemory> usableMemories) {
        Objects.requireNonNull(usableMemories, "usableMemories");
        Companion companion = companionRepository.findByOwnerId(subjectId)
                .filter(value -> value.id() != null && value.id() == companionId)
                .orElseThrow(() -> new IllegalStateException("伙伴不存在: " + companionId));
        StringBuilder prompt = new StringBuilder();
        prompt.append(LingyePersona.systemPrompt());
        // 状态只用来调语气：不给数值、不用内部阶段名，避免她把状态当话题念出来。
        prompt.append("\n## 现在的情况（只用来调整语气，不要主动说出来）\n");
        prompt.append("- 成长：").append(growthLabel(companion)).append("（你的名字始终是绫页）\n");
        prompt.append("- 心情：").append(moodLabel(currentMood)).append("\n");
        prompt.append("- 和对方：").append(relationshipLabel(companionId, subjectId)).append("\n");
        String leanings = traitLeanings(companion.traits());
        if (!leanings.isEmpty()) {
            prompt.append("- 最近的小倾向：").append(leanings)
                    .append("（只影响细节，不改变上面的性格）\n");
        }

        // 防御性状态过滤：输入已由调用方做过撤权扫描与 CONFIRMED 过滤，这里不再查询数据库。
        List<CompanionMemory> memories = usableMemories.stream()
                .filter(memory -> memory.status() == MemoryStatus.CONFIRMED
                        && memory.companionId() == companionId && memory.subjectId() == subjectId)
                .limit(Math.max(0, Math.min(memoryLimit, 10)))
                .toList();
        // 没有相关记录时整段不出现：空段落本身也会把她的注意力拉回图片。
        if (!memories.isEmpty()) {
            prompt.append("\n## 这轮可能用得上的记录（用得上再用，用不上就当没看到）\n");
            for (CompanionMemory memory : memories) {
                prompt.append("- [").append(memorySource(memory)).append("] ")
                        .append(truncate(memory.content(), MEMORY_CODE_POINT_LIMIT)).append("\n");
            }
        }
        prompt.append("""

                ## 补充
                - 一般不超过 60 字；对方让你解释或讲点东西时可以长一些，但不超过 200 字。
                - 历史对话里你以前的一些回复是旧版本的你写的，可能又长又端着。那不是现在的你，不要模仿那种口吻。
                """);
        return prompt.toString();
    }

    private static String growthLabel(Companion companion) {
        return switch (companion.lifeStage()) {
            case LIGHT -> "刚诞生不久";
            case SEEDLING -> "正在长大";
            case COMPANION -> "已经长成";
        };
    }

    /** 情绪只换算成一个粗略的词；没有明显（≥60）的维度就是"平常"。0 是中性，不代表疲惫。 */
    private static String moodLabel(CompanionMood mood) {
        if (mood == null) {
            return "平常";
        }
        String[] labels = {"精神不错", "挺不错", "有点闷", "脑子里点子挺多", "有点烦"};
        BigDecimal[] values = {mood.energy(), mood.joy(), mood.loneliness(),
                mood.inspiration(), mood.irritation()};
        int strongest = -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i].compareTo(STRONG_MOOD) >= 0
                    && (strongest < 0 || values[i].compareTo(values[strongest]) > 0)) {
                strongest = i;
            }
        }
        return strongest < 0 ? "平常" : labels[strongest];
    }

    private String relationshipLabel(long companionId, long subjectId) {
        return relationshipRepository.findByCompanionAndSubject(companionId, subjectId)
                .map(CompanionChatContextAssembler::describeRelationship)
                .orElse("刚认识");
    }

    private static String describeRelationship(CompanionRelationship relationship) {
        BigDecimal closeness = relationship.familiarity().add(relationship.trust())
                .add(relationship.closeness()).add(relationship.tacit())
                .divide(BigDecimal.valueOf(4), 2, java.math.RoundingMode.HALF_UP);
        String label = closeness.compareTo(new BigDecimal("50")) >= 0
                ? "已经挺熟，玩笑可以更放开"
                : closeness.compareTo(new BigDecimal("20")) >= 0 ? "渐渐熟悉" : "刚认识";
        return relationship.recentFeedback().signum() < 0 ? label + "；最近有点小摩擦" : label;
    }

    /** 成长中积累出的明显倾向（|值|>30），最多两项，只作为语气细节。 */
    private static String traitLeanings(CompanionTraits traits) {
        List<String> leanings = new java.util.ArrayList<>();
        addLeaning(leanings, "好奇", "谨慎", traits.curiosity());
        addLeaning(leanings, "热情", "克制", traits.enthusiasm());
        addLeaning(leanings, "爱玩", "沉稳", traits.playfulness());
        addLeaning(leanings, "共情", "理性", traits.empathy());
        addLeaning(leanings, "爱创造", "讲秩序", traits.creativity());
        return String.join("、", leanings.subList(0, Math.min(2, leanings.size())));
    }

    private static void addLeaning(List<String> leanings, String positive, String negative, BigDecimal value) {
        BigDecimal v = Objects.requireNonNull(value, "trait value");
        if (v.compareTo(STRONG_TRAIT) > 0) {
            leanings.add("更" + positive + "一点");
        } else if (v.compareTo(STRONG_TRAIT.negate()) < 0) {
            leanings.add("更" + negative + "一点");
        }
    }

    private static String memorySource(CompanionMemory memory) {
        String picture = memory.pictureId() == null ? "对方的记录" : "对方上传的图片 #" + memory.pictureId();
        String date = MEMORY_DATE.format(memory.createdTime().atZone(SHANGHAI));
        String origin = memory.content().equals(memory.originalContent())
                ? "视觉模型的客观描述" : "对方亲自改过的描述";
        return picture + " · " + date + " · " + origin;
    }

    private static String truncate(String content, int maxCodePoints) {
        if (content.codePointCount(0, content.length()) <= maxCodePoints) {
            return content;
        }
        int end = content.offsetByCodePoints(0, maxCodePoints);
        return content.substring(0, end) + "……";
    }
}
