package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.companion.view.ChatHistoryView;
import com.li.lipicturecloud.config.CompanionFeatureProperties;
import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.domain.companion.CompanionChatMessage;
import com.li.lipicturecloud.domain.companion.CompanionChatMessageRepository;
import com.li.lipicturecloud.domain.companion.CompanionMemory;
import com.li.lipicturecloud.domain.companion.CompanionMemoryRepository;
import com.li.lipicturecloud.domain.companion.CompanionMoodRepository;
import com.li.lipicturecloud.domain.companion.CompanionRelationshipRepository;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
import com.li.lipicturecloud.domain.companion.MemorySourceType;
import com.li.lipicturecloud.domain.companion.MemoryStatus;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanionChatServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T08:00:00Z");
    private final AuthorizationSubject subject = AuthorizationSubject.user(7L);

    private CompanionRepository companionRepository;
    private CompanionChatMessageRepository messageRepository;
    private CompanionMoodRepository moodRepository;
    private CompanionRelationshipRepository relationshipRepository;
    private CompanionMemoryRepository memoryRepository;
    private CompanionChatContextAssembler contextAssembler;
    private ChatQuotaGuard quotaGuard;
    private CompanionFeatureProperties properties;
    @SuppressWarnings("unchecked")
    private ObjectProvider<ChatModel> chatModelProvider = mock(ObjectProvider.class);
    private CompanionChatService service;

    @BeforeEach
    void setUp() {
        companionRepository = mock(CompanionRepository.class);
        messageRepository = mock(CompanionChatMessageRepository.class);
        moodRepository = mock(CompanionMoodRepository.class);
        relationshipRepository = mock(CompanionRelationshipRepository.class);
        memoryRepository = mock(CompanionMemoryRepository.class);
        contextAssembler = mock(CompanionChatContextAssembler.class);
        quotaGuard = mock(ChatQuotaGuard.class);
        properties = new CompanionFeatureProperties();
        when(messageRepository.append(any())).thenAnswer(invocation ->
                invocation.<CompanionChatMessage>getArgument(0).withId(51L));
        when(messageRepository.findRecent(anyLong(), anyInt())).thenReturn(List.of());
        when(memoryRepository.findRecent(anyLong(), anyInt())).thenReturn(List.of());
        service = new CompanionChatService(companionRepository, messageRepository, moodRepository,
                relationshipRepository, memoryRepository, contextAssembler, quotaGuard, properties,
                chatModelProvider, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void demoReplyRoutesOnMemoryKeywordAndDoesNotCallTheModel() {
        Companion companion = persistedCompanion();
        CompanionMemory confirmed = CompanionMemory.candidate(companion.id(), 7L, 101L, 21L,
                MemorySourceType.VISUAL, "伙伴记得一张明亮的图片。", new BigDecimal("0.8"), NOW)
                .confirm(NOW);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(memoryRepository.findRecent(companion.id(), 100)).thenReturn(List.of(confirmed));

        String reply = service.chat(subject, "你还记得什么吗").blockLast();

        assertThat(reply).contains("1 条确认的记忆");
        verify(quotaGuard).reserve(7L, java.time.LocalDate.parse("2026-08-14"), 50);
        verify(chatModelProvider, never()).getIfAvailable();
        verify(messageRepository, times(2)).append(any());
    }

    @Test
    void demoReplyFallsBackWhenNoKeywordMatches() {
        Companion companion = persistedCompanion();
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));

        String reply = service.chat(subject, "你好呀").blockLast();

        assertThat(reply).contains("我在听");
        assertThat(reply).contains("喂给我");
    }

    @Test
    void rejectsBlankOrOversizedMessagesBeforeQuota() {
        Companion companion = persistedCompanion();
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));

        assertThatThrownBy(() -> service.chat(subject, "   "))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(ErrorCode.PARAMS_ERROR.getCode());
        assertThatThrownBy(() -> service.chat(subject, "长".repeat(501)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(ErrorCode.PARAMS_ERROR.getCode());
        verify(quotaGuard, never()).reserve(anyLong(), any(), anyInt());
    }

    @Test
    void historyReturnsMessagesInChronologicalOrder() {
        Companion companion = persistedCompanion();
        CompanionChatMessage older = CompanionChatMessage.user(companion.id(), 7L, "第一句", NOW);
        CompanionChatMessage newer = CompanionChatMessage.companion(companion.id(), 7L, "第二句",
                "internal", "demo-v1", NOW.plusSeconds(60));
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(messageRepository.findRecent(companion.id(), 50)).thenReturn(List.of(newer, older));

        ChatHistoryView history = service.history(subject, 50);

        assertThat(history.records()).hasSize(2);
        assertThat(history.records().get(0).content()).isEqualTo("第一句");
        assertThat(history.records().get(1).content()).isEqualTo("第二句");
    }

    @Test
    void historyRequiresAnAwakenedCompanion() {
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.history(subject, 50))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND_ERROR.getCode());
    }

    private Companion persistedCompanion() {
        return Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
    }
}
