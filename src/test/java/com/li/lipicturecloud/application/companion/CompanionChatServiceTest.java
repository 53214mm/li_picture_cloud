package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.airuntime.ConnectivityResult;
import com.li.lipicturecloud.application.airuntime.ModelInvocationException;
import com.li.lipicturecloud.application.airuntime.ModelRouteDecision;
import com.li.lipicturecloud.application.companion.view.ChatHistoryView;
import com.li.lipicturecloud.config.CompanionFeatureProperties;
import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
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
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
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
    private com.li.lipicturecloud.application.airuntime.LanguageRouter languageRouter;
    private com.li.lipicturecloud.application.airuntime.LanguageModelInvoker languageInvoker;
    private com.li.lipicturecloud.application.airuntime.ModelUsageService modelUsageService;
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
        languageRouter = mock(com.li.lipicturecloud.application.airuntime.LanguageRouter.class);
        languageInvoker = mock(com.li.lipicturecloud.application.airuntime.LanguageModelInvoker.class);
        modelUsageService = mock(com.li.lipicturecloud.application.airuntime.ModelUsageService.class);
        when(messageRepository.append(any())).thenAnswer(invocation ->
                invocation.<CompanionChatMessage>getArgument(0).withId(51L));
        when(messageRepository.findRecent(anyLong(), anyInt())).thenReturn(List.of());
        when(memoryRepository.findRecent(anyLong(), anyInt())).thenReturn(List.of());
        when(languageRouter.decide(anyLong())).thenReturn(
                com.li.lipicturecloud.application.airuntime.ModelRouteDecision.platform());
        service = new CompanionChatService(companionRepository, messageRepository, moodRepository,
                relationshipRepository, memoryRepository, contextAssembler, quotaGuard, properties,
                chatModelProvider, languageRouter, languageInvoker, modelUsageService,
                Clock.fixed(NOW, ZoneOffset.UTC));
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

    @Test
    void modelReplyDropsOldestHistoryWhenContextBudgetIsExceeded() {
        Companion companion = persistedCompanion();
        properties.setChatPolicy(CompanionFeatureProperties.CompanionChatPolicy.MODEL);
        properties.setChatContextBudget(400);
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse chunk = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(chunk));
        when(chunk.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(new AssistantMessage("伙伴的回复"));
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(contextAssembler.systemPrompt(11L, 7L, 5)).thenReturn("系统提示");
        // 三条历史（倒序），每条 120 码点；预算 400 只装得下最近的约两条。
        String longText = "历史消息".repeat(30);
        CompanionChatMessage latest = CompanionChatMessage.companion(companion.id(), 7L, longText,
                "internal", "demo-v1", NOW.plusSeconds(3));
        CompanionChatMessage middle = CompanionChatMessage.user(companion.id(), 7L, longText, NOW.plusSeconds(2));
        CompanionChatMessage oldest = CompanionChatMessage.companion(companion.id(), 7L, longText,
                "internal", "demo-v1", NOW.plusSeconds(1));
        when(messageRepository.findRecent(companion.id(), 20))
                .thenReturn(List.of(latest, middle, oldest));

        String reply = service.chat(subject, "你好").blockLast();

        assertThat(reply).isEqualTo("伙伴的回复");
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(captor.capture());
        List<Message> messages = captor.getValue().getInstructions();
        assertThat(messages).hasSize(4); // 系统 + 2 条历史 + 当前消息
        assertThat(messages.get(0).getText()).isEqualTo("系统提示");
        assertThat(messages.get(messages.size() - 1).getText()).isEqualTo("你好");
    }

    @Test
    void modelReplyKeepsSystemAndCurrentMessageEvenWhenBudgetIsTiny() {
        Companion companion = persistedCompanion();
        properties.setChatPolicy(CompanionFeatureProperties.CompanionChatPolicy.MODEL);
        properties.setChatContextBudget(50);
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse chunk = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(chunk));
        when(chunk.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(new AssistantMessage("伙伴的回复"));
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(contextAssembler.systemPrompt(11L, 7L, 5)).thenReturn("系统提示");
        when(messageRepository.findRecent(companion.id(), 20))
                .thenReturn(List.of(CompanionChatMessage.companion(companion.id(), 7L,
                        "历史消息".repeat(30), "internal", "demo-v1", NOW.plusSeconds(3))));

        service.chat(subject, "你好").blockLast();

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(captor.capture());
        // 极小预算下历史全部被丢弃，但系统提示与当前消息必须保留。
        List<Message> messages = captor.getValue().getInstructions();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getText()).isEqualTo("系统提示");
        assertThat(messages.get(1).getText()).isEqualTo("你好");
    }

    private Companion persistedCompanion() {
        return Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
    }

    private ModelConnection byokConnection() {
        return ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK, "主力",
                URI.create("https://api.deepseek.com/v1"), "deepseek-chat", 5L, true, 1L);
    }

    @Test
    void byokRouteUsesUserConnectionAndRecordsByokUsage() {
        Companion companion = persistedCompanion();
        properties.setChatPolicy(CompanionFeatureProperties.CompanionChatPolicy.MODEL);
        when(languageRouter.decide(7L)).thenReturn(
                ModelRouteDecision.byok(byokConnection(), "sk-secret"));
        when(languageInvoker.stream(any(ModelRouteDecision.class), anyList()))
                .thenReturn(Flux.just("你", "好"));
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(contextAssembler.systemPrompt(11L, 7L, 5)).thenReturn("系统提示");

        String reply = String.join("", service.chat(subject, "在吗").collectList().block());

        assertThat(reply).isEqualTo("你好");
        verify(chatModelProvider, never()).getIfAvailable();
        verify(modelUsageService).recordSuccess(7L, ModelTask.LANGUAGE_AGENT, 9L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK);
        ArgumentCaptor<CompanionChatMessage> captor = ArgumentCaptor.forClass(CompanionChatMessage.class);
        verify(messageRepository, times(2)).append(captor.capture());
        CompanionChatMessage replyMessage = captor.getAllValues().get(1);
        assertThat(replyMessage.modelProvider()).isEqualTo("DEEPSEEK");
        assertThat(replyMessage.modelCode()).isEqualTo("deepseek-chat");
        assertThat(replyMessage.content()).isEqualTo("你好");
    }

    @Test
    void byokFailureRecordsSafeCodeAndNeverFallsBackToPlatform() {
        Companion companion = persistedCompanion();
        properties.setChatPolicy(CompanionFeatureProperties.CompanionChatPolicy.MODEL);
        when(languageRouter.decide(7L)).thenReturn(
                ModelRouteDecision.byok(byokConnection(), "sk-secret"));
        when(languageInvoker.stream(any(ModelRouteDecision.class), anyList()))
                .thenReturn(Flux.error(new ModelInvocationException(
                        ConnectivityResult.CREDENTIAL_REJECTED, "rejected")));
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(contextAssembler.systemPrompt(11L, 7L, 5)).thenReturn("系统提示");

        assertThatThrownBy(() -> service.chat(subject, "在吗").blockLast())
                .isInstanceOf(ModelInvocationException.class);

        verify(chatModelProvider, never()).getIfAvailable();
        verify(modelUsageService).recordFailure(7L, ModelTask.LANGUAGE_AGENT, 9L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK,
                ConnectivityResult.CREDENTIAL_REJECTED);
        // 只有用户消息落库，不留下半截回复。
        verify(messageRepository, times(1)).append(any());
    }

    @Test
    void brokenRouteFailsBeforeQuotaOrMessageAreWritten() {
        Companion companion = persistedCompanion();
        properties.setChatPolicy(CompanionFeatureProperties.CompanionChatPolicy.MODEL);
        when(languageRouter.decide(7L)).thenThrow(new BusinessException(
                ErrorCode.OPERATION_ERROR, "语言任务路由的连接已停用，请启用或清除路由规则"));
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));

        assertThatThrownBy(() -> service.chat(subject, "你好"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("路由的连接已停用");

        // 路由决定在任何写入之前：坏路由不得吞掉用户消息或消耗额度。
        verify(quotaGuard, never()).reserve(anyLong(), any(), anyInt());
        verify(messageRepository, never()).append(any());
    }
}
