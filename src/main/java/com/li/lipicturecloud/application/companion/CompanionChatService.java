package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.companion.view.ChatHistoryView;
import com.li.lipicturecloud.application.companion.view.ChatMessageView;
import com.li.lipicturecloud.config.CompanionFeatureProperties;
import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionChatMessage;
import com.li.lipicturecloud.domain.companion.CompanionChatMessageRepository;
import com.li.lipicturecloud.domain.companion.CompanionMemory;
import com.li.lipicturecloud.domain.companion.CompanionMood;
import com.li.lipicturecloud.domain.companion.CompanionMoodRepository;
import com.li.lipicturecloud.domain.companion.CompanionMemoryRepository;
import com.li.lipicturecloud.domain.companion.CompanionRelationship;
import com.li.lipicturecloud.domain.companion.CompanionRelationshipRepository;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
import com.li.lipicturecloud.domain.companion.MemoryStatus;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 伙伴站内对话：历史查询与一轮流式回复。
 *
 * <p>DEMO_ONLY 档不发起模型调用；MODEL 档只把"本轮消息 + 服务端组装的可解释上下文 +
 * 最近历史"交给语言模型，并先预占每日轮次额度。消息只追加，不修改不删除。</p>
 */
@Service
@ConditionalOnProperty(prefix = "app.companion", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CompanionChatService {

    private static final Logger log = LoggerFactory.getLogger(CompanionChatService.class);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int MAX_USER_CODE_POINTS = 500;

    private final CompanionRepository companionRepository;
    private final CompanionChatMessageRepository messageRepository;
    private final CompanionMoodRepository moodRepository;
    private final CompanionRelationshipRepository relationshipRepository;
    private final CompanionMemoryRepository memoryRepository;
    private final CompanionChatContextAssembler contextAssembler;
    private final ChatQuotaGuard quotaGuard;
    private final CompanionFeatureProperties properties;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final Clock clock;

    public CompanionChatService(CompanionRepository companionRepository,
                                CompanionChatMessageRepository messageRepository,
                                CompanionMoodRepository moodRepository,
                                CompanionRelationshipRepository relationshipRepository,
                                CompanionMemoryRepository memoryRepository,
                                CompanionChatContextAssembler contextAssembler,
                                ChatQuotaGuard quotaGuard,
                                CompanionFeatureProperties properties,
                                ObjectProvider<ChatModel> chatModelProvider,
                                Clock clock) {
        this.companionRepository = companionRepository;
        this.messageRepository = messageRepository;
        this.moodRepository = moodRepository;
        this.relationshipRepository = relationshipRepository;
        this.memoryRepository = memoryRepository;
        this.contextAssembler = contextAssembler;
        this.quotaGuard = quotaGuard;
        this.properties = properties;
        this.chatModelProvider = chatModelProvider;
        this.clock = clock;
    }

    public ChatHistoryView history(AuthorizationSubject subject, int limit) {
        Objects.requireNonNull(subject, "subject");
        Companion companion = requireCompanion(subject);
        List<CompanionChatMessage> recent = messageRepository.findRecent(companion.id(), boundedLimit(limit));
        List<ChatMessageView> records = new ArrayList<>(recent.size());
        // 存储为倒序；历史按时间正序返回，便于前端直接追加渲染。
        for (int i = recent.size() - 1; i >= 0; i--) {
            records.add(view(recent.get(i)));
        }
        return new ChatHistoryView(List.copyOf(records));
    }

    public Flux<String> chat(AuthorizationSubject subject, String message) {
        Objects.requireNonNull(subject, "subject");
        String normalized = validateMessage(message);
        Companion companion = requireCompanion(subject);
        quotaGuard.reserve(subject.userId(), LocalDate.now(clock.withZone(SHANGHAI)),
                properties.getChatDailyLimit());
        Instant now = clock.instant();
        messageRepository.append(CompanionChatMessage.user(companion.id(), subject.userId(), normalized, now));
        log.info("companion_chat_message_sent subjectId={} policy={}",
                subject.userId(), properties.getChatPolicy().name());

        if (properties.getChatPolicy() == CompanionFeatureProperties.CompanionChatPolicy.MODEL) {
            return modelReply(companion, subject, normalized, now);
        }
        String reply = demoReply(companion.id(), subject.userId(), normalized);
        return Flux.just(reply).doOnComplete(() ->
                persistReply(companion, subject, reply, "internal", "demo-v1", now));
    }

    private Flux<String> modelReply(Companion companion, AuthorizationSubject subject, String message, Instant now) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "伙伴对话模型暂不可用");
        }
        String systemPrompt = contextAssembler.systemPrompt(companion.id(), subject.userId(),
                properties.getChatMemoryLimit());
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        List<CompanionChatMessage> history = messageRepository.findRecent(
                companion.id(), properties.getChatHistoryLimit());
        for (int i = history.size() - 1; i >= 0; i--) {
            CompanionChatMessage past = history.get(i);
            messages.add(past.role().name().equals("USER")
                    ? new UserMessage(past.content()) : new AssistantMessage(past.content()));
        }
        messages.add(new UserMessage(message));
        StringBuilder collected = new StringBuilder();
        return chatModel.stream(new Prompt(messages))
                .map(chunk -> {
                    String text = chunk.getResult() == null || chunk.getResult().getOutput() == null
                            ? "" : chunk.getResult().getOutput().getText();
                    if (text != null) {
                        collected.append(text);
                    }
                    return text == null ? "" : text;
                })
                .doOnComplete(() -> persistReply(companion, subject,
                        collected.toString(), "dashscope", "qwen-max", now))
                .doOnError(error -> log.warn("companion_chat_model_failed subjectId={} exceptionType={}",
                        subject.userId(), error.getClass().getName()));
    }

    private String demoReply(long companionId, long subjectId, String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        long confirmed = memoryRepository.findRecent(companionId, 100).stream()
                .filter(memory -> memory.status() == MemoryStatus.CONFIRMED)
                .count();
        if (containsAny(normalized, "记忆", "记得", "记住", "回忆")) {
            return confirmed > 0
                    ? String.format("我记得最近留下过 %d 条确认的记忆，它们都来自你喂给我的图片。想听哪一段？", confirmed)
                    : "我现在还没有确认的记忆。喂我一张图片，我就能开始记住我们的经历。";
        }
        if (containsAny(normalized, "情绪", "心情", "感觉", "状态", "累")) {
            return moodRepository.findByCompanionId(companionId)
                    .map(mood -> "我此刻的精力是 " + plain(mood.energy()) + "，愉悦 " + plain(mood.joy())
                            + "。喂图会让我波动，安静一会儿就会平复。")
                    .orElse("我还没有明显情绪，先喂我一张图片吧。");
        }
        if (containsAny(normalized, "关系", "熟悉", "信任", "默契", "我们")) {
            return relationshipRepository.findByCompanionAndSubject(companionId, subjectId)
                    .map(relationship -> "我们越来越熟了：熟悉度 " + plain(relationship.familiarity())
                            + "，信任 " + plain(relationship.trust()) + "。继续相处下去吧。")
                    .orElse("我们才刚认识，多喂我几张图片，我会更懂你。");
        }
        return "我在听。你可以和我聊聊图片，或者从图库里挑一张喂给我，我会慢慢记住我们的经历。";
    }

    private void persistReply(Companion companion, AuthorizationSubject subject, String reply,
                              String provider, String model, Instant now) {
        String content = reply == null || reply.isBlank() ? "（这次没有想好怎么回）" : reply.strip();
        if (content.codePointCount(0, content.length()) > CompanionChatMessage.MAX_CONTENT_CODE_POINTS) {
            int end = content.offsetByCodePoints(0, CompanionChatMessage.MAX_CONTENT_CODE_POINTS);
            content = content.substring(0, end);
        }
        messageRepository.append(CompanionChatMessage.companion(
                companion.id(), subject.userId(), content, provider, model, now));
        log.info("companion_chat_reply_completed subjectId={} provider={} model={} characters={}",
                subject.userId(), provider, model, content.codePointCount(0, content.length()));
    }

    private ChatMessageView view(CompanionChatMessage message) {
        return new ChatMessageView(message.id(), message.role().name(), message.content(),
                message.modelProvider(), message.modelCode(), message.createdTime());
    }

    private Companion requireCompanion(AuthorizationSubject subject) {
        return companionRepository.findByOwnerId(subject.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "请先唤醒伙伴"));
    }

    private String validateMessage(String message) {
        String normalized = Objects.requireNonNull(message, "message").strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > MAX_USER_CODE_POINTS) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息需为 1-500 字");
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息包含不支持的字符");
        }
        return normalized;
    }

    private static boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String plain(java.math.BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static int boundedLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }
}
