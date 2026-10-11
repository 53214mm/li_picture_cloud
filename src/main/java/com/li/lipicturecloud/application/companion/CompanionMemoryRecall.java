package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.CompanionMemory;
import com.li.lipicturecloud.domain.companion.MemoryStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 本轮聊天该想起哪些图片记忆。
 *
 * <p>输入必须是已通过来源授权检查的已确认记忆。召回顺序：对方点名的图片编号 → 向量库语义检索
 * （pgvector）与字面两字匹配。所有命中都与输入求交集，所以已删除、忽略或
 * 撤权的记忆即使还留在向量库里也不会被用到。</p>
 */
@Component
public class CompanionMemoryRecall {

    private static final Logger log = LoggerFactory.getLogger(CompanionMemoryRecall.class);
    /** 首次接入向量库时每轮最多补索引的记忆数，避免一轮聊天卡太久。 */
    private static final int MAX_INDEX_PER_TURN = 20;
    private static final int RECENT_PICTURE_LIMIT = 3;
    private static final Pattern PICTURE_REFERENCE = Pattern.compile(
            "图片|照片|(?:那|这|刚才那|刚喂的)张图|picture|喂.{0,6}(?:图|照片)|(?:图|照片).{0,4}记忆", Pattern.CASE_INSENSITIVE);
    private static final Pattern FOLLOWUP = Pattern.compile("^(?:现在[呢哩]?|刚喂了|刚刚喂了)[？?！!。\\s]*$|(?:那|这|刚才那|刚喂的)张(?:图|照片)");
    private static final Pattern SHORT_FOLLOWUP = Pattern.compile(
            "^(?:现在[呢哩]?|刚喂了|刚刚喂了|(?:那|这)张(?:图|照片)(?:呢|吗)?)[？?！!。\\s]*$");
    private static final Pattern PICTURE_ID = Pattern.compile("(?:图片|照片|picture|#)\\s*(?:#|ID|id)?\\s*(\\d{1,19})(?!\\d)");
    /** 对方在问"喂过哪些图、记不记得图"这类整体问题，而不是某一张。 */
    private static final Pattern ASKS_ABOUT_PICTURES = Pattern.compile(
            "(喂|发|给|传|上传).{0,6}(图|照片)|(哪些|什么|几张|多少).{0,4}(图|照片)|记(得|住).{0,8}(图|照片)|(图|照片).{0,6}(记(得|住)|记忆)");

    private final CompanionMemoryIndex index;
    private final PictureLabelProvider labels;
    private final double minSimilarity;

    public CompanionMemoryRecall(@Qualifier("companionMemoryIndex") CompanionMemoryIndex index,
                                 PictureLabelProvider labels,
                                 @Value("${app.companion.memory-vector.min-similarity:0.45}") double minSimilarity) {
        this.index = Objects.requireNonNull(index, "index");
        this.labels = Objects.requireNonNull(labels, "labels");
        this.minSimilarity = minSimilarity;
    }

    public MemoryRecall recall(String query, List<CompanionMemory> usable, long companionId, long subjectId,
                               int limit) {
        return recall(query, usable, companionId, subjectId, limit, List.of());
    }

    /** 历史只接受当前会话内的用户发言，最近在前；不从模型自己说过的话推断图片。 */
    public MemoryRecall recall(String query, List<CompanionMemory> usable, long companionId, long subjectId,
                               int limit, List<String> recentUserQueries) {
        return recallInternal(query, usable, companionId, subjectId, limit, recentUserQueries, true);
    }

    /** Observation never probes, synchronizes or queries the paid vector index. */
    public MemoryRecall recallLocally(String query, List<CompanionMemory> usable, long companionId, long subjectId,
                                      int limit, List<String> recentUserQueries) {
        return recallInternal(query, usable, companionId, subjectId, limit, recentUserQueries, false);
    }

    private MemoryRecall recallInternal(String query, List<CompanionMemory> usable, long companionId, long subjectId,
                                        int limit, List<String> recentUserQueries, boolean useVector) {
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
        Map<Long, String> documents = new LinkedHashMap<>();
        owned.forEach(memory -> {
            byId.put(memory.id(), memory);
            documents.put(memory.id(), documentText(memory, labelOf(memory, pictureLabels)));
        });

        List<Long> mentioned = mentionedPictures(query, owned);
        LinkedHashSet<Long> selected = new LinkedHashSet<>(mentioned);
        selected.addAll(RecallIndexSync.lexical(query, documents));
        boolean followup = FOLLOWUP.matcher(query.strip()).find();
        boolean ambiguous = false;
        if (followup && pictureCount(owned.stream().filter(memory -> selected.contains(memory.id())).toList()) > 1) {
            selected.clear();
            ambiguous = true;
        }
        if (followup && !ambiguous && selected.isEmpty() && !PICTURE_ID.matcher(query).find()) {
            // 最多略过三条内的短指代追问；遇到实际新话题就停，不越过闲聊找旧图。
            String prior = recentUserQueries.stream().limit(3)
                    .filter(past -> !SHORT_FOLLOWUP.matcher(past.strip()).matches()
                            || PICTURE_ID.matcher(past).find()
                            || !RecallIndexSync.lexical(past, documents).isEmpty())
                    .findFirst().orElse("");
            boolean directPictureReference = PICTURE_REFERENCE.matcher(query).find();
            boolean priorHasId = PICTURE_ID.matcher(prior).find();
            LinkedHashSet<Long> references = new LinkedHashSet<>(mentionedPictures(prior, owned));
            if (!priorHasId) {
                references.addAll(RecallIndexSync.lexical(prior, documents));
            }
            boolean pictureTopic = directPictureReference || priorHasId
                    || ASKS_ABOUT_PICTURES.matcher(prior).find()
                    || (!references.isEmpty() && PICTURE_REFERENCE.matcher(prior).find());
            if (pictureTopic) {
                List<CompanionMemory> candidates = references.isEmpty() ? owned
                        : owned.stream().filter(memory -> references.contains(memory.id())).toList();
                if (pictureCount(candidates) == 1 && (!references.isEmpty()
                        || (directPictureReference && !priorHasId))) {
                    candidates.forEach(memory -> selected.add(memory.id()));
                } else {
                    ambiguous = true;
                }
            }
        }
        String source = "LEXICAL";
        // 指代解析不另做语义猜测，也不因为这次历史辅助解析增加索引请求。
        if (useVector && !followup && index.available() && bounded > 0) {
            try {
                RecallIndexSync.reconcile(index, companionId, subjectId, documents, MAX_INDEX_PER_TURN);
                index.search(companionId, subjectId, query, bounded * 2 + 2).stream()
                        .filter(hit -> hit.similarity() >= minSimilarity && byId.containsKey(hit.memoryId()))
                        .sorted(Comparator.comparingDouble(CompanionMemoryIndex.Hit::similarity).reversed())
                        .forEach(hit -> selected.add(hit.memoryId()));
                source = "VECTOR";
            } catch (CompanionMemoryIndex.IndexUnavailableException unavailable) {
                // 字面命中已经保留；向量故障不改变授权交集。
            }
        }
        List<RecalledMemory> recalled = selected.stream()
                .map(byId::get).filter(Objects::nonNull).limit(bounded)
                .map(memory -> new RecalledMemory(memory, labelOf(memory, pictureLabels)))
                .toList();
        // 点名了具体哪张图（"还记得图片 101 吗"）就只说那张，不再把最近的图都列出来。
        List<RecalledMemory> recent = selected.isEmpty() && !PICTURE_ID.matcher(query).find()
                && (ambiguous || ASKS_ABOUT_PICTURES.matcher(query).find())
                ? owned.stream().limit(Math.min(bounded, RECENT_PICTURE_LIMIT))
                        .map(memory -> new RecalledMemory(memory, labelOf(memory, pictureLabels))).toList()
                : List.of();
        log.debug("companion_memory_recall subjectId={} source={} recalled={}", subjectId, source, recalled.size());
        return new MemoryRecall(recalled, recent, pictureCount(owned), ambiguous);
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

    static String documentText(CompanionMemory memory, PictureLabel label) {
        String described = label == null ? "" : label.describe();
        return described.isEmpty() ? memory.content() : described + " " + memory.content();
    }

    private static PictureLabel labelOf(CompanionMemory memory, Map<Long, PictureLabel> pictureLabels) {
        return memory.pictureId() == null ? null : pictureLabels.get(memory.pictureId());
    }

    /** 一条想起的记忆，带上对方给图片写的名字和标签（可能为空）。 */
    public record RecalledMemory(CompanionMemory memory, PictureLabel label) {
    }

    /**
     * @param recalled       本轮相关的记忆
     * @param recentPictures 对方在问"喂过哪些图"时给出的最近几张；否则为空
     * @param pictureCount   她记得的图片总数，用来让她知道自己有记忆
     */
    public record MemoryRecall(List<RecalledMemory> recalled, List<RecalledMemory> recentPictures, int pictureCount,
                               boolean referenceAmbiguous) {
        public MemoryRecall(List<RecalledMemory> recalled, List<RecalledMemory> recentPictures, int pictureCount) {
            this(recalled, recentPictures, pictureCount, false);
        }
        public MemoryRecall {
            recalled = List.copyOf(recalled);
            recentPictures = List.copyOf(recentPictures);
        }

        public static MemoryRecall empty() {
            return new MemoryRecall(List.of(), List.of(), 0);
        }
    }
}
