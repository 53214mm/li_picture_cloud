package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.airuntime.ModelRouteDecision;
import com.li.lipicturecloud.domain.companion.CompanionChatEpisode;
import com.li.lipicturecloud.domain.companion.CompanionChatEpisodeRepository;
import com.li.lipicturecloud.domain.companion.CompanionChatMessage;
import com.li.lipicturecloud.domain.companion.CompanionChatMessageRepository;
import com.li.lipicturecloud.domain.companion.CompanionChatRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 聊天经历：把结束的对话整理成经历，聊天时按相关程度想起来。
 *
 * <p>新的一段对话开始时，整理上一段（最多最近两段；更早积压的旧记录直接跳过，不逐段补）。
 * 整理失败不影响聊天，下一轮再试。</p>
 */
@Component
public class CompanionEpisodeService {

    private static final Logger log = LoggerFactory.getLogger(CompanionEpisodeService.class);
    private static final int MAX_PENDING_MESSAGES = 120;
    private static final int MAX_EPISODES_PER_TURN = 2;
    /** 对方说的话加起来太少（比如只有"你好"）时不调模型，直接记为没有内容。 */
    private static final int MIN_USER_CODE_POINTS = 8;
    private static final int RECALL_SCAN = 60;
    private static final int RECALL_LIMIT = 3;

    private final CompanionChatMessageRepository messages;
    private final CompanionChatEpisodeRepository episodes;
    private final ChatEpisodeSummarizer summarizer;
    private final CompanionEpisodeRecall recall;
    private final CompanionChatSessions sessions;
    private final Clock clock;

    public CompanionEpisodeService(CompanionChatMessageRepository messages, CompanionChatEpisodeRepository episodes,
                                   ChatEpisodeSummarizer summarizer, CompanionEpisodeRecall recall,
                                   CompanionChatSessions sessions, Clock clock) {
        this.messages = messages;
        this.episodes = episodes;
        this.summarizer = summarizer;
        this.recall = recall;
        this.sessions = sessions;
        this.clock = clock;
    }

    /**
     * 整理当前这一段开始之前、还没整理过的对话。
     *
     * @param sessionStart     当前这一段开始的时间
     * @param currentMessageId 本轮刚发出的消息，永远不算进旧对话
     */
    public void consolidate(long companionId, long subjectId, ModelRouteDecision route,
                            Instant sessionStart, long currentMessageId) {
        List<CompanionChatMessage> pending;
        try {
            long covered = episodes.lastCoveredMessageId(companionId);
            pending = new ArrayList<>(messages.findOlder(companionId, covered, sessionStart, MAX_PENDING_MESSAGES)
                    .stream().filter(message -> !Objects.equals(message.id(), currentMessageId)).toList());
        } catch (RuntimeException failure) {
            log.warn("companion_episode_scan_failed subjectId={} exceptionType={}",
                    subjectId, failure.getClass().getName());
            return;
        }
        if (pending.isEmpty()) {
            return;
        }
        java.util.Collections.reverse(pending);
        List<List<CompanionChatMessage>> groups = CompanionChatSessions.split(pending, sessions.gap());
        for (List<CompanionChatMessage> group : groups.subList(Math.max(0, groups.size() - MAX_EPISODES_PER_TURN),
                groups.size())) {
            ChatEpisodeSummarizer.Summary summary;
            if (userCodePoints(group) < MIN_USER_CODE_POINTS) {
                summary = ChatEpisodeSummarizer.Summary.nothing();
            } else {
                try {
                    summary = summarizer.summarize(route, group);
                } catch (RuntimeException failure) {
                    // 不推进整理进度，下一轮再试；不记录聊天内容。
                    log.warn("companion_episode_summary_failed subjectId={} exceptionType={}",
                            subjectId, failure.getClass().getName());
                    return;
                }
            }
            CompanionChatMessage first = group.getFirst();
            CompanionChatMessage last = group.getLast();
            Instant ended = last.createdTime().isBefore(first.createdTime()) ? first.createdTime() : last.createdTime();
            boolean stored = episodes.append(new CompanionChatEpisode(null, companionId, subjectId,
                    first.id(), last.id(), first.createdTime(), ended, summary.text(),
                    summary.modelProvider(), summary.modelCode(), clock.instant()));
            log.info("companion_episode_consolidated subjectId={} messages={} memorable={} stored={}",
                    subjectId, group.size(), !summary.text().isEmpty(), stored);
        }
    }

    /** 本轮可能用得上的以前的聊天。 */
    public List<CompanionChatEpisode> recall(String query, long companionId, long subjectId) {
        return recall.recall(query, episodes.findRecent(companionId, RECALL_SCAN), companionId, subjectId,
                RECALL_LIMIT);
    }

    /** 清空聊天记录时一并清掉从聊天整理出的经历（向量在下次召回时对账删除）。 */
    public int clear(long companionId) {
        return episodes.deleteByCompanion(companionId);
    }

    private static int userCodePoints(List<CompanionChatMessage> group) {
        return group.stream().filter(message -> message.role() == CompanionChatRole.USER)
                .mapToInt(message -> message.content().codePointCount(0, message.content().length())).sum();
    }
}
