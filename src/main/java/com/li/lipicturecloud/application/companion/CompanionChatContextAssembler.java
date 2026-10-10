package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionChatEpisode;
import com.li.lipicturecloud.domain.companion.CompanionMemory;
import com.li.lipicturecloud.domain.companion.CompanionMood;
import com.li.lipicturecloud.domain.companion.CompanionRelationship;
import com.li.lipicturecloud.domain.companion.CompanionRelationshipRepository;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
import com.li.lipicturecloud.domain.companion.CompanionTraits;
import com.li.lipicturecloud.domain.companion.MemoryStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
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

    /** 视觉记忆现在包含观察、可能的意义和角色，最长约 300 字；超过这个预算才截断。 */
    private static final int MEMORY_CODE_POINT_LIMIT = 240;
    private static final int RECENT_PICTURE_CODE_POINT_LIMIT = 60;
    private static final BigDecimal STRONG_MOOD = new BigDecimal("60");
    private static final BigDecimal STRONG_TRAIT = new BigDecimal("30");
    private static final java.time.ZoneId SHANGHAI = java.time.ZoneId.of("Asia/Shanghai");
    private static final java.time.format.DateTimeFormatter MEMORY_DATE =
            java.time.format.DateTimeFormatter.ofPattern("M月d日");
    private static final java.time.format.DateTimeFormatter CLOCK =
            java.time.format.DateTimeFormatter.ofPattern(" H:mm");
    private static final String[] WEEKDAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final int EPISODE_CODE_POINT_LIMIT = 120;

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
        List<CompanionMemory> owned = usableMemories.stream()
                .filter(memory -> memory.status() == MemoryStatus.CONFIRMED
                        && memory.companionId() == companionId && memory.subjectId() == subjectId)
                .toList();
        return systemPrompt(companionId, subjectId, memoryLimit, currentMood,
                new CompanionMemoryRecall.MemoryRecall(owned.stream()
                        .map(memory -> new CompanionMemoryRecall.RecalledMemory(memory, null)).toList(),
                        List.of(), CompanionMemoryRecall.pictureCount(owned)));
    }

    public String systemPrompt(long companionId, long subjectId, int memoryLimit,
                               CompanionMood currentMood, CompanionMemoryRecall.MemoryRecall recall) {
        return systemPrompt(companionId, subjectId, memoryLimit, currentMood, recall, Conversation.unknown());
    }

    /**
     * 组装系统提示词。
     *
     * @param recall       本轮召回结果：相关记忆（已授权、已确认）、对方问起时的最近图片、记得的图片总数
     * @param conversation 现在的时间、这句话是不是接着正在聊的、以及本轮可能用得上的以前的聊天
     */
    public String systemPrompt(long companionId, long subjectId, int memoryLimit,
                               CompanionMood currentMood, CompanionMemoryRecall.MemoryRecall recall,
                               Conversation conversation) {
        Objects.requireNonNull(recall, "recall");
        Objects.requireNonNull(conversation, "conversation");
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

        // 让她知道自己记得图片，但不主动提。
        prompt.append("- 记忆：").append(recall.pictureCount() > 0
                ? "你记得对方喂过的 " + recall.pictureCount() + " 张图；对方问起或聊到相关的才用，不主动提"
                : "对方还没有喂过让你记住的图").append("\n");

        // 防御性状态过滤：输入已由调用方做过撤权扫描与 CONFIRMED 过滤，这里不再查询数据库。
        List<CompanionMemoryRecall.RecalledMemory> memories = owned(recall.recalled(), companionId, subjectId)
                .limit(Math.max(0, Math.min(memoryLimit, 10)))
                .toList();
        // 没有相关记录时整段不出现：空段落本身也会把她的注意力拉回图片。
        if (!memories.isEmpty()) {
            prompt.append("\n## 这轮可能用得上的记录（用得上再用，用不上就当没看到）\n");
            for (CompanionMemoryRecall.RecalledMemory recalled : memories) {
                prompt.append("- [").append(memorySource(recalled)).append("] ")
                        .append(truncate(recalled.memory().content(), MEMORY_CODE_POINT_LIMIT)).append("\n");
            }
        }
        appendTime(prompt, conversation);
        List<CompanionChatEpisode> episodes = conversation.episodes().stream()
                .filter(episode -> episode.memorable() && episode.companionId() == companionId
                        && episode.subjectId() == subjectId)
                .toList();
        if (!episodes.isEmpty() && conversation.now() != null) {
            prompt.append("\n## 你记得的以前的聊天（对方提起、或者这句话明显接着那件事时才用；"
                    + "说的时候讲清楚是什么时候的事，比如\"上午你说……\"）\n");
            for (CompanionChatEpisode episode : episodes) {
                prompt.append("- ").append(when(episode.startedTime(), episode.endedTime(), conversation.now()))
                        .append("：")
                        .append(truncate(episode.summary(), EPISODE_CODE_POINT_LIMIT)).append("\n");
            }
        }
        List<CompanionMemoryRecall.RecalledMemory> recent = owned(recall.recentPictures(), companionId, subjectId)
                .toList();
        if (!recent.isEmpty()) {
            prompt.append("\n## 对方最近喂过的图（对方正在问这个，可以照着说，不要编多出来的）\n");
            for (CompanionMemoryRecall.RecalledMemory recalled : recent) {
                prompt.append("- ").append(pictureName(recalled)).append("：")
                        .append(truncate(recalled.memory().content(), RECENT_PICTURE_CODE_POINT_LIMIT)).append("\n");
            }
        }
        prompt.append("""

                ## 补充
                - 一般不超过 60 字；对方让你解释或讲点东西时可以长一些，但不超过 200 字。
                - 历史对话里你以前的一些回复是旧版本的你写的，可能又长又端着。那不是现在的你，不要模仿那种口吻。
                - 对方问起以前的事，而上面没有对应的记录时，就说记不太清了，不要拿别的事硬凑，也不要编。
                """);
        return prompt.toString();
    }

    /** 现在几点、距离上次说话多久：新的一段对话不接着上一段的话头。 */
    private static void appendTime(StringBuilder prompt, Conversation conversation) {
        if (conversation.now() == null) {
            return;
        }
        java.time.ZonedDateTime now = conversation.now().atZone(SHANGHAI);
        prompt.append("\n## 时间\n- 现在是 ").append(MEMORY_DATE.format(now))
                .append("（").append(WEEKDAYS[now.getDayOfWeek().getValue() - 1]).append("）")
                .append(CLOCK.format(now)).append("。\n");
        Instant previous = conversation.previousMessageAt();
        if (previous == null) {
            prompt.append("- 你们之前没有聊天记录。\n");
        } else if (conversation.continuing()) {
            prompt.append("- 你们正在聊，上一句是").append(elapsed(previous, conversation.now())).append("。\n");
        } else {
            prompt.append("- 这是新的一段对话，你们上次说话是").append(elapsed(previous, conversation.now()))
                    .append("。之前聊的事已经过去了，对方没提起、这句话也不是接着那件事，就不要主动扯回去。\n");
        }
    }

    /** 粗略的时间间隔，比如"刚刚"" 3 分钟前""约 5 小时前"" 2 天前"。 */
    static String elapsed(Instant from, Instant to) {
        java.time.Duration gap = java.time.Duration.between(from, to);
        if (gap.isNegative() || gap.toMinutes() < 1) {
            return "刚刚";
        }
        if (gap.toMinutes() < 60) {
            return " " + gap.toMinutes() + " 分钟前";
        }
        if (gap.toHours() < 24) {
            return "约 " + gap.toHours() + " 小时前";
        }
        return " " + gap.toDays() + " 天前";
    }

    /** 经历发生在什么时候：刚才（一小时内结束）、今天上午、昨天晚上、3 天前、9月27日。 */
    static String when(Instant at, Instant ended, Instant now) {
        if (java.time.Duration.between(ended, now).toMinutes() < 60) {
            return "刚才";
        }
        java.time.ZonedDateTime moment = at.atZone(SHANGHAI);
        long days = java.time.temporal.ChronoUnit.DAYS.between(moment.toLocalDate(), now.atZone(SHANGHAI).toLocalDate());
        int hour = moment.getHour();
        String part = hour < 5 ? "凌晨" : hour < 11 ? "上午" : hour < 13 ? "中午" : hour < 18 ? "下午" : "晚上";
        if (days <= 0) {
            return "今天" + part;
        }
        if (days == 1) {
            return "昨天" + part;
        }
        if (days == 2) {
            return "前天" + part;
        }
        return days <= 6 ? days + " 天前" : MEMORY_DATE.format(moment);
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

    /** 防御：只放行本伙伴、本用户的已确认记忆。 */
    private static java.util.stream.Stream<CompanionMemoryRecall.RecalledMemory> owned(
            List<CompanionMemoryRecall.RecalledMemory> memories, long companionId, long subjectId) {
        return memories.stream().filter(recalled -> recalled.memory().status() == MemoryStatus.CONFIRMED
                && recalled.memory().companionId() == companionId && recalled.memory().subjectId() == subjectId);
    }

    private static String pictureName(CompanionMemoryRecall.RecalledMemory recalled) {
        CompanionMemory memory = recalled.memory();
        String named = recalled.label() == null || recalled.label().name().isEmpty()
                ? "一张没起名的图" : "《" + recalled.label().name() + "》";
        return named + "（" + MEMORY_DATE.format(memory.createdTime().atZone(SHANGHAI)) + "）";
    }

    private static String memorySource(CompanionMemoryRecall.RecalledMemory recalled) {
        CompanionMemory memory = recalled.memory();
        String described = recalled.label() == null ? "" : recalled.label().describe();
        String picture = memory.pictureId() == null ? "对方的记录"
                : "对方上传的图片 #" + memory.pictureId() + (described.isEmpty() ? "" : " " + described);
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

    /**
     * 这句话所处的对话情况。
     *
     * @param now               本轮的时间；为 {@code null} 时不写时间段落
     * @param previousMessageAt 上一句话的时间；从没聊过为 {@code null}
     * @param continuing        是否接着正在进行的这一段对话
     * @param episodes          本轮可能用得上的以前的聊天
     */
    public record Conversation(Instant now, Instant previousMessageAt, boolean continuing,
                               List<CompanionChatEpisode> episodes) {
        public Conversation {
            episodes = List.copyOf(episodes);
        }

        public static Conversation unknown() {
            return new Conversation(null, null, false, List.of());
        }
    }
}
