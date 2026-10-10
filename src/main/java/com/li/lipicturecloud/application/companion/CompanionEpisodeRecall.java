package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.CompanionChatEpisode;
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
import java.util.regex.Pattern;

/**
 * 本轮聊天该想起哪些以前聊过的事。
 *
 * <p>和图片记忆一样走 pgvector 语义检索，向量库不可用时退回字面匹配；对方明确在问以前聊过什么
 * （"上次""昨天说的"）时，再补上最近的一两段。不相关的经历不进提示词。</p>
 */
@Component
public class CompanionEpisodeRecall {

    private static final Logger log = LoggerFactory.getLogger(CompanionEpisodeRecall.class);
    private static final int MAX_INDEX_PER_TURN = 20;
    private static final int RECENT_WHEN_ASKED = 2;
    /** 对方在问以前聊过的事（只出现"下午"这类时间词不算，比如"今天下午要面试"）。 */
    private static final Pattern ASKS_ABOUT_PAST = Pattern.compile(
            "上次|(之前|以前|上午|中午|下午|昨天|昨晚|前天|那天|前几天).{0,8}(说|聊|提|讲)|聊过|说过|提过|还记得");

    private final CompanionMemoryIndex index;
    private final double minSimilarity;

    public CompanionEpisodeRecall(@Qualifier("companionEpisodeIndex") CompanionMemoryIndex index,
                                  @Value("${app.companion.memory-vector.min-similarity:0.45}") double minSimilarity) {
        this.index = Objects.requireNonNull(index, "index");
        this.minSimilarity = minSimilarity;
    }

    /**
     * @param recentNewestFirst 最近的经历（新→旧），可以包含没有内容的占位段
     */
    public List<CompanionChatEpisode> recall(String query, List<CompanionChatEpisode> recentNewestFirst,
                                             long companionId, long subjectId, int limit) {
        Objects.requireNonNull(query, "query");
        Map<Long, CompanionChatEpisode> byId = new LinkedHashMap<>();
        Map<Long, String> documents = new LinkedHashMap<>();
        recentNewestFirst.stream()
                .filter(episode -> episode.id() != null && episode.memorable()
                        && episode.companionId() == companionId && episode.subjectId() == subjectId)
                .forEach(episode -> {
                    byId.put(episode.id(), episode);
                    documents.put(episode.id(), episode.summary());
                });
        int bounded = Math.max(0, Math.min(limit, 5));
        LinkedHashSet<Long> selected = new LinkedHashSet<>();
        if (ASKS_ABOUT_PAST.matcher(query).find()) {
            byId.keySet().stream().limit(RECENT_WHEN_ASKED).forEach(selected::add);
        }
        String source = "LEXICAL";
        if (index.available()) {
            try {
                // 即使没有经历也对账一次：清空聊天记录后把旧向量删掉。
                RecallIndexSync.reconcile(index, companionId, subjectId, documents, MAX_INDEX_PER_TURN);
                if (!byId.isEmpty()) {
                    index.search(companionId, subjectId, query, bounded * 2 + 2).stream()
                            .filter(hit -> hit.similarity() >= minSimilarity && byId.containsKey(hit.memoryId()))
                            .sorted(Comparator.comparingDouble(CompanionMemoryIndex.Hit::similarity).reversed())
                            .forEach(hit -> selected.add(hit.memoryId()));
                }
                source = "VECTOR";
            } catch (CompanionMemoryIndex.IndexUnavailableException unavailable) {
                selected.addAll(RecallIndexSync.lexical(query, documents));
            }
        } else {
            selected.addAll(RecallIndexSync.lexical(query, documents));
        }
        List<CompanionChatEpisode> recalled = selected.stream().map(byId::get).filter(Objects::nonNull)
                .limit(bounded).toList();
        log.debug("companion_episode_recall subjectId={} source={} recalled={}", subjectId, source, recalled.size());
        return recalled;
    }
}
