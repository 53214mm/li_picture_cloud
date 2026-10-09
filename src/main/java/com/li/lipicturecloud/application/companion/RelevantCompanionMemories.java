package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.CompanionMemory;
import com.li.lipicturecloud.domain.companion.MemoryStatus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Conservative lexical recall only; input MUST already have passed the source authorization guard. */
public final class RelevantCompanionMemories {
    private static final List<String> TOPICS = List.of(
            "猫", "狗", "兔", "鸟", "鲜花", "花朵", "玫瑰", "海边", "海滩", "日落", "日出", "夕阳", "星空", "雪山",
            "森林", "建筑", "婚礼", "生日", "毕业", "旅行", "咖啡", "蛋糕", "自行车", "汽车", "火车",
            // Explicit multi-character entities from the accepted recall examples; no inferred synonyms/ngrams.
            "机械女仆", "粉色双马尾");
    private static final Set<String> STOP_WORDS = Set.of("the", "this", "that", "image", "picture", "photo",
            "remember", "hello", "what", "about", "with", "have", "you", "your", "and", "for");
    private static final Pattern WORD = Pattern.compile("[a-z][a-z0-9_-]{2,39}");
    private static final Pattern QUOTED = Pattern.compile("[“\"「]([^”\"」]{2,40})[”\"」]");
    private static final Pattern PICTURE_ID = Pattern.compile("(?:图片|照片|picture)\\s*(?:#|ID|id)?\\s*(\\d{1,19})(?!\\d)");

    private RelevantCompanionMemories() { }

    public static List<CompanionMemory> select(String query, List<CompanionMemory> authorized,
                                                long companionId, long subjectId, int limit) {
        String normalized = query.toLowerCase(Locale.ROOT);
        Set<String> terms = new LinkedHashSet<>();
        TOPICS.stream().filter(normalized::contains).forEach(terms::add);
        WORD.matcher(normalized).results().map(match -> match.group()).filter(word -> !STOP_WORDS.contains(word))
                .forEach(terms::add);
        QUOTED.matcher(normalized).results().map(match -> match.group(1)).forEach(terms::add);
        Set<String> pictureIds = new LinkedHashSet<>();
        PICTURE_ID.matcher(normalized).results().map(match -> match.group(1)).forEach(pictureIds::add);
        return authorized.stream()
                .filter(memory -> memory.companionId() == companionId && memory.subjectId() == subjectId
                        && memory.status() == MemoryStatus.CONFIRMED)
                .filter(memory -> pictureIds.contains(String.valueOf(memory.pictureId()))
                        || terms.stream().anyMatch(term -> matches(memory.content().toLowerCase(Locale.ROOT), term)))
                .limit(Math.max(0, Math.min(limit, 10))).toList();
    }

    private static boolean matches(String content, String term) {
        // Latin words match token boundaries, not arbitrary substrings (cat != category).
        return term.chars().allMatch(c -> c < 128)
                ? Pattern.compile("(?<![a-z0-9_])" + Pattern.quote(term) + "(?![a-z0-9_])").matcher(content).find()
                : content.contains(term);
    }
}
