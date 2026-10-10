package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.config.CompanionFeatureProperties;
import com.li.lipicturecloud.domain.companion.ChatSessionBoundaryRepository;
import com.li.lipicturecloud.domain.companion.CompanionChatMessage;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 把聊天记录切成"一段一段"的对话。
 *
 * <p>新的一段从这两种时候开始：重新登录；或者距离上一句超过 {@code app.companion.chat-session-gap}
 * （默认 2 小时）。只有当前这一段的消息原样交给模型、显示在聊天列表里；更早的段落整理成经历，
 * 相关时才想起。</p>
 */
@Component
public class CompanionChatSessions {

    private final ChatSessionBoundaryRepository boundaries;
    private final Duration gap;

    public CompanionChatSessions(ChatSessionBoundaryRepository boundaries, CompanionFeatureProperties properties) {
        this.boundaries = Objects.requireNonNull(boundaries, "boundaries");
        this.gap = Objects.requireNonNull(properties.getChatSessionGap(), "chatSessionGap");
        if (gap.isNegative() || gap.isZero()) {
            throw new IllegalArgumentException("chatSessionGap must be positive");
        }
    }

    public Duration gap() {
        return gap;
    }

    /**
     * @param newestFirst 最近的消息（新→旧），不含本轮刚发出的那句
     * @param now         本轮的时间
     */
    public Session current(long subjectId, List<CompanionChatMessage> newestFirst, Instant now) {
        return cut(newestFirst, boundaries.lastLogin(subjectId).orElse(null), now, gap);
    }

    static Session cut(List<CompanionChatMessage> newestFirst, Instant lastLogin, Instant now, Duration gap) {
        List<CompanionChatMessage> session = new ArrayList<>();
        Instant cursor = now;
        for (CompanionChatMessage message : newestFirst) {
            Instant at = message.createdTime();
            if (lastLogin != null && at.isBefore(lastLogin) || Duration.between(at, cursor).compareTo(gap) > 0) {
                break;
            }
            session.add(message);
            cursor = at;
        }
        Instant previous = newestFirst.isEmpty() ? null : newestFirst.getFirst().createdTime();
        Instant start = session.isEmpty() ? now : session.getLast().createdTime();
        return new Session(session, start, previous);
    }

    /** 把旧→新的消息按间隔切成若干段（每段旧→新）。 */
    static List<List<CompanionChatMessage>> split(List<CompanionChatMessage> oldestFirst, Duration gap) {
        List<List<CompanionChatMessage>> groups = new ArrayList<>();
        List<CompanionChatMessage> current = new ArrayList<>();
        for (CompanionChatMessage message : oldestFirst) {
            if (!current.isEmpty()
                    && Duration.between(current.getLast().createdTime(), message.createdTime()).compareTo(gap) > 0) {
                groups.add(List.copyOf(current));
                current.clear();
            }
            current.add(message);
        }
        if (!current.isEmpty()) {
            groups.add(List.copyOf(current));
        }
        return groups;
    }

    /**
     * @param messagesNewestFirst 当前这一段里已有的消息（新→旧）
     * @param start               这一段开始的时间；还没有消息时就是现在
     * @param previousMessageAt   上一句话（不论是不是这一段）的时间；从没聊过为 {@code null}
     */
    public record Session(List<CompanionChatMessage> messagesNewestFirst, Instant start, Instant previousMessageAt) {
        public Session {
            messagesNewestFirst = List.copyOf(messagesNewestFirst);
            Objects.requireNonNull(start, "start");
        }

        /** 这句话是不是接着正在进行的对话。 */
        public boolean continuing() {
            return !messagesNewestFirst.isEmpty();
        }
    }
}
