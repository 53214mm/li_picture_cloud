package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.CompanionMemory;
import com.li.lipicturecloud.domain.companion.MemoryStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 本轮聊天该想起哪些图片记忆。
 *
 * <p>输入必须是已通过来源授权检查的已确认记忆。召回顺序：对方点名的图片编号 → 向量库语义检索
 * （pgvector）→ 向量库不可用时退回字面两字匹配。所有命中都与输入求交集，所以已删除、忽略或
 * 撤权的记忆即使还留在向量库里也不会被用到。</p>
 */
@Component
public class CompanionMemoryRecall {

    private static final Logger log = LoggerFactory.getLogger(CompanionMemoryRecall.class);
    /** 首次接入向量库时每轮最多补索引的记忆数，避免一轮聊天卡太久。 */
    private static final int MAX_INDEX_PER_TURN = 20;
    private static final int RECENT_PICTURE_LIMIT = 6;
    private static final Pattern PICTURE_ID = Pattern.compile("(?:图片|照片|picture|#)\\s*(?:#|ID|id)?\\s*(\\d{1,19})(?!\\d)");
    /** 对方在问"喂过哪些图、记不记得图"这类整体问题，而不是某一张。 */
    private static final Pattern ASKS_ABOUT_PICTURES = Pattern.compile(
            "(喂|发|给|传|上传).{0,6}(图|照片)|(哪些|什么|几张|多少).{0,4}(图|照片)|记(得|住).{0,8}(图|照片)|(图|照片).{0,6}记(得|住)");
    private static final Pattern CJK = Pattern.compile("[\\p{IsHan}]+");
    private static final Pattern LATIN = Pattern.compile("[a-z0-9][a-z0-9_-]{2,39}");
    /** 太常见、不足以说明在聊同一张图的两字组合。 */
    private static final Set<String> STOP_BIGRAMS = Set.of("图片", "照片", "这张", "那张", "一张", "之前", "记得",
            "还记", "什么", "哪些", "我们", "你的", "我的", "可能", "画面", "一个", "没有", "这个", "那个", "观察",
            "可见", "推测", "意义", "角色", "出处", "对方", "上传", "喂过", "给你", "你还", "现在", "今天");

    private final CompanionMemoryIndex index;
    private final PictureLabelProvider labels;
    private final double minSimilarity;

    public CompanionMemoryRecall(CompanionMemoryIndex index, PictureLabelProvider labels,
                                 @Value("${app.companion.memory-vector.min-similarity:0.45}") double minSimilarity) {
        this.index = Objects.requireNonNull(index, "index");
        this.labels = Objects.requireNonNull(labels, "labels");
        this.minSimilarity = minSimilarity;
    }

    public MemoryRecall recall(String query, List<CompanionMemory> usable, long companionId, long subjectId,
                               int limit) {
        Objects.requireNonNull(query, "query");
        List<CompanionMemory> owned = usable.stream()
                .filter(memory -> memory.companionId() == companionId && memory.subjectId() == subjectId
                        && memory.status() == MemoryStatus.CONFIRMED)
                .toList();
        int bounded = Math.max(0, Math.min(limit, 10));
        if (owned.isEmpty()) {
            return MemoryRecall.empty();
        }
        Map<Long, PictureLabel> pictureLabels = labels.labels(owned.stream()
                .map(CompanionMemory::pictureId).filter(Objects::nonNull).distinct().toList());
        Map<Long, CompanionMemory> byId = new LinkedHashMap<>();
        owned.forEach(memory -> byId.put(memory.id(), memory));

        LinkedHashSet<Long> selected = new LinkedHashSet<>(mentionedPictures(query, owned));
        String source = "LEXICAL";
        if (index.available()) {
            try {
                reconcile(owned, pictureLabels, companionId, subjectId);
                index.search(companionId, subjectId, query, bounded * 2 + 2).stream()
                        .filter(hit -> hit.similarity() >= minSimilarity && byId.containsKey(hit.memoryId()))
                        .sorted(Comparator.comparingDouble(CompanionMemoryIndex.Hit::similarity).reversed())
                        .forEach(hit -> selected.add(hit.memoryId()));
                source = "VECTOR";
            } catch (CompanionMemoryIndex.IndexUnavailableException unavailable) {
                selected.addAll(lexical(query, owned, pictureLabels));
            }
        } else {
            selected.addAll(lexical(query, owned, pictureLabels));
        }
        List<RecalledMemory> recalled = selected.stream()
                .map(byId::get).filter(Objects::nonNull).limit(bounded)
                .map(memory -> new RecalledMemory(memory, labelOf(memory, pictureLabels)))
                .toList();
        List<RecalledMemory> recent = ASKS_ABOUT_PICTURES.matcher(query).find()
                ? owned.stream().limit(RECENT_PICTURE_LIMIT)
                        .map(memory -> new RecalledMemory(memory, labelOf(memory, pictureLabels))).toList()
                : List.of();
        log.debug("companion_memory_recall subjectId={} source={} recalled={}", subjectId, source, recalled.size());
        return new MemoryRecall(recalled, recent, pictureCount(owned));
    }

    /** 补齐向量库缺少或被纠正过的记忆，并清掉已不可用的旧向量。 */
    private void reconcile(List<CompanionMemory> owned, Map<Long, PictureLabel> pictureLabels,
                           long companionId, long subjectId) {
        Map<Long, String> indexed = index.fingerprints(companionId, subjectId);
        List<CompanionMemoryIndex.Entry> missing = new ArrayList<>();
        for (CompanionMemory memory : owned) {
            String text = documentText(memory, labelOf(memory, pictureLabels));
            String fingerprint = fingerprint(text);
            if (!fingerprint.equals(indexed.get(memory.id())) && missing.size() < MAX_INDEX_PER_TURN) {
                missing.add(new CompanionMemoryIndex.Entry(memory.id(), text, fingerprint));
            }
        }
        index.upsert(companionId, subjectId, missing);
        Set<Long> live = new LinkedHashSet<>();
        owned.forEach(memory -> live.add(memory.id()));
        List<Long> stale = indexed.keySet().stream().filter(id -> !live.contains(id)).toList();
        index.remove(stale);
    }

    /** 记得的图片张数；没有来源图片的记忆各算一条。 */
    static int pictureCount(List<CompanionMemory> memories) {
        return (int) memories.stream()
                .map(memory -> memory.pictureId() == null ? "memory-" + memory.id() : "picture-" + memory.pictureId())
                .distinct().count();
    }

    private static List<Long> mentionedPictures(String query, List<CompanionMemory> owned) {
        Set<String> ids = new LinkedHashSet<>();
        PICTURE_ID.matcher(query).results().forEach(match -> ids.add(match.group(1)));
        return owned.stream().filter(memory -> memory.pictureId() != null
                && ids.contains(String.valueOf(memory.pictureId()))).map(CompanionMemory::id).toList();
    }

    /** 字面兜底：对方这句话与记忆内容、图片名、标签共享的两字组合越多越靠前。 */
    static List<Long> lexical(String query, List<CompanionMemory> owned, Map<Long, PictureLabel> pictureLabels) {
        Set<String> terms = terms(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> scores = new LinkedHashMap<>();
        for (CompanionMemory memory : owned) {
            Set<String> documentTerms = terms(documentText(memory, labelOf(memory, pictureLabels)));
            int score = (int) terms.stream().filter(documentTerms::contains).count();
            if (score > 0) {
                scores.put(memory.id(), score);
            }
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey).toList();
    }

    static Set<String> terms(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        Set<String> terms = new LinkedHashSet<>();
        CJK.matcher(lower).results().map(match -> match.group()).forEach(run -> {
            for (int i = 0; i + 1 < run.length(); i++) {
                String bigram = run.substring(i, i + 2);
                if (!STOP_BIGRAMS.contains(bigram)) {
                    terms.add(bigram);
                }
            }
        });
        LATIN.matcher(lower).results().map(match -> match.group()).forEach(terms::add);
        return terms;
    }

    static String documentText(CompanionMemory memory, PictureLabel label) {
        String described = label == null ? "" : label.describe();
        return described.isEmpty() ? memory.content() : described + " " + memory.content();
    }

    private static PictureLabel labelOf(CompanionMemory memory, Map<Long, PictureLabel> pictureLabels) {
        return memory.pictureId() == null ? null : pictureLabels.get(memory.pictureId());
    }

    private static String fingerprint(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** 一条想起的记忆，带上对方给图片写的名字和标签（可能为空）。 */
    public record RecalledMemory(CompanionMemory memory, PictureLabel label) {
    }

    /**
     * @param recalled       本轮相关的记忆
     * @param recentPictures 对方在问"喂过哪些图"时给出的最近几张；否则为空
     * @param pictureCount   她记得的图片总数，用来让她知道自己有记忆
     */
    public record MemoryRecall(List<RecalledMemory> recalled, List<RecalledMemory> recentPictures, int pictureCount) {
        public MemoryRecall {
            recalled = List.copyOf(recalled);
            recentPictures = List.copyOf(recentPictures);
        }

        public static MemoryRecall empty() {
            return new MemoryRecall(List.of(), List.of(), 0);
        }
    }
}
