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
import com.li.lipicturecloud.domain.companion.CompanionMood;
import com.li.lipicturecloud.domain.companion.CompanionMoodRepository;
import com.li.lipicturecloud.domain.companion.CompanionMoodRules;
import com.li.lipicturecloud.domain.companion.CompanionRelationshipRepository;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
import com.li.lipicturecloud.domain.companion.MemorySourceType;
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
import reactor.core.Disposable;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private CompanionMemoryService memoryService;
    private CompanionChatContextAssembler contextAssembler;
    private ChatQuotaGuard quotaGuard;
    private CompanionFeatureProperties properties;
    @SuppressWarnings("unchecked")
    private ObjectProvider<ChatModel> chatModelProvider = mock(ObjectProvider.class);
    private com.li.lipicturecloud.application.airuntime.LanguageRouter languageRouter;
    private com.li.lipicturecloud.application.airuntime.LanguageModelInvoker languageInvoker;
    private com.li.lipicturecloud.application.airuntime.ModelUsageService modelUsageService;
    private com.li.lipicturecloud.application.airuntime.PlatformTrialLedgerService trialLedger;
    private CompanionChatService service;
    private CompanionChatModeService modeService;
    private com.li.lipicturecloud.domain.companion.ChatSessionBoundaryRepository boundaries;
    private CompanionEpisodeService episodes;

    @Test
    void aReplyStartedBeforeClearCannotRestoreDeletedHistory() {
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(persistedCompanion()));
        Flux<String> oldReply = service.chat(subject, "清空前启动的回复");
        service.clearHistory(subject);
        oldReply.blockLast();
        verify(messageRepository, times(1)).append(any());
    }

    @Test
    void oldConversationSummariesCannotDelayTheCurrentFirstDelta() {
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(persistedCompanion()));
        properties.setChatPolicy(CompanionFeatureProperties.CompanionChatPolicy.MODEL);
        ChatModel model = mock(ChatModel.class);
        when(chatModelProvider.getIfAvailable()).thenReturn(model);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("当前回复"))))));
        when(contextAssembler.systemPrompt(anyLong(), anyLong(), anyInt(), any(), any(), any())).thenReturn("绫页");
        java.util.concurrent.atomic.AtomicBoolean firstDelta = new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doAnswer(call -> {
            assertThat(firstDelta.get()).as("summary must follow the current first delta").isTrue();
            return null;
        }).when(episodes).schedule(anyLong(), anyLong(), any(), any(), anyLong());
        assertThat(service.chat(subject, "这是当前这一轮").doOnNext(delta -> firstDelta.set(true)).collectList().block())
                .containsExactly("当前回复");
    }

    @Test
    void aContinuousConversationOutgrowingThePromptWindowIsNotSummarised() {
        Companion companion = persistedCompanion();
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        properties.setChatPolicy(CompanionFeatureProperties.CompanionChatPolicy.MODEL);
        ChatModel model = mock(ChatModel.class);
        when(chatModelProvider.getIfAvailable()).thenReturn(model);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("收到"))))));
        when(contextAssembler.systemPrompt(anyLong(), anyLong(), anyInt(), any(), any(), any())).thenReturn("绫页");
        java.util.ArrayList<CompanionChatMessage> history = new java.util.ArrayList<>();
        for (int i = 30; i >= 1; i--) {
            history.add(CompanionChatMessage.user(11L, 7L, "连续聊天第" + i + "句话",
                    NOW.minusSeconds((31 - i) * 300L)).withId((long) i));
        }
        when(messageRepository.findRecent(11L, properties.getChatHistoryLimit() + 1))
                .thenReturn(history.subList(0, properties.getChatHistoryLimit() + 1));
        CompanionChatMessage edge = history.get(properties.getChatHistoryLimit() - 1);
        when(messageRepository.findBefore(11L, edge.createdTime(), edge.id(), 100))
                .thenReturn(history.subList(properties.getChatHistoryLimit(), history.size()));
        service.chat(subject, "继续刚才的话题").collectList().block();
        verify(episodes).schedule(eq(11L), eq(7L), any(), eq(NOW.minusSeconds(30 * 300L)), eq(51L));
    }

    @BeforeEach
    void setUp() {
        companionRepository = mock(CompanionRepository.class);
        messageRepository = mock(CompanionChatMessageRepository.class);
        moodRepository = mock(CompanionMoodRepository.class);
        relationshipRepository = mock(CompanionRelationshipRepository.class);
        memoryService = mock(CompanionMemoryService.class);
        contextAssembler = mock(CompanionChatContextAssembler.class);
        quotaGuard = mock(ChatQuotaGuard.class);
        properties = new CompanionFeatureProperties();
        languageRouter = mock(com.li.lipicturecloud.application.airuntime.LanguageRouter.class);
        languageInvoker = mock(com.li.lipicturecloud.application.airuntime.LanguageModelInvoker.class);
        modelUsageService = mock(com.li.lipicturecloud.application.airuntime.ModelUsageService.class);
        trialLedger = mock(com.li.lipicturecloud.application.airuntime.PlatformTrialLedgerService.class);
        boundaries = mock(com.li.lipicturecloud.domain.companion.ChatSessionBoundaryRepository.class);
        episodes = mock(CompanionEpisodeService.class);
        java.util.Map<Long, CompanionChatMessage> stored = new java.util.HashMap<>();
        when(messageRepository.append(any())).thenAnswer(invocation -> {
            CompanionChatMessage saved = invocation.<CompanionChatMessage>getArgument(0).withId(51L);
            stored.put(saved.id(), saved);
            return saved;
        });
        when(messageRepository.findById(anyLong())).thenAnswer(call -> Optional.ofNullable(stored.get(call.<Long>getArgument(0))));
        when(messageRepository.deleteByCompanion(anyLong())).thenAnswer(call -> {
            int size = stored.size(); stored.clear(); return size;
        });
        when(companionRepository.findByOwnerIdForUpdate(anyLong())).thenAnswer(call ->
                companionRepository.findByOwnerId(call.<Long>getArgument(0)));
        org.springframework.transaction.PlatformTransactionManager manager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        when(messageRepository.findRecent(anyLong(), anyInt())).thenReturn(List.of());
        when(memoryService.confirmedMemoriesUsableBy(any(), any(), anyInt())).thenReturn(List.of());
        when(languageRouter.decide(anyLong())).thenReturn(
                com.li.lipicturecloud.application.airuntime.ModelRouteDecision.platform());
        modeService = org.mockito.Mockito.spy(chatModeService(properties));
        service = new CompanionChatService(companionRepository, messageRepository, moodRepository,
                CompanionMoodRules.v1(), relationshipRepository, memoryService, contextAssembler,
                quotaGuard, properties, chatModelProvider, languageRouter, languageInvoker,
                modelUsageService, trialLedger, modeService,
                new CompanionMemoryRecall(new com.li.lipicturecloud.infrastructure.companion.UnavailableCompanionMemoryIndex(),
                        ids -> java.util.Map.of(), 0.45),
                new CompanionChatSessions(boundaries, properties), episodes,
                new CompanionChatWrites(companionRepository, messageRepository,
                        mock(com.li.lipicturecloud.domain.companion.CompanionChatEpisodeRepository.class), manager),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** 用户从未选择过模式、也没有自己的模型：模式由服务端策略决定（与原行为一致）。 */
    private static CompanionChatModeService chatModeService(CompanionFeatureProperties properties) {
        com.li.lipicturecloud.domain.companion.CompanionChatPreferenceRepository preferences =
                mock(com.li.lipicturecloud.domain.companion.CompanionChatPreferenceRepository.class);
        when(preferences.findMode(anyLong())).thenReturn(java.util.Optional.empty());
        com.li.lipicturecloud.application.airuntime.ModelQuickSetupService quickSetup =
                mock(com.li.lipicturecloud.application.airuntime.ModelQuickSetupService.class);
        when(quickSetup.current(anyLong())).thenReturn(
                com.li.lipicturecloud.application.airuntime.view.QuickConnectionView.platform());
        ObjectProvider<ChatModel> availablePlatform = mock(ObjectProvider.class);
        when(availablePlatform.getIfAvailable()).thenReturn(mock(ChatModel.class));
        return new CompanionChatModeService(preferences, quickSetup, properties, availablePlatform);
    }

    @Test
    void demoReplyRoutesOnMemoryKeywordAndDoesNotCallTheModel() {
        Companion companion = persistedCompanion();
        CompanionMemory confirmed = CompanionMemory.candidate(companion.id(), 7L, 101L, 21L,
                MemorySourceType.VISUAL, "伙伴记得一张明亮的图片。", new BigDecimal("0.8"), NOW)
                .confirm(NOW);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(memoryService.confirmedMemoriesUsableBy(companion, subject, 100))
                .thenReturn(List.of(confirmed));

        String reply = service.chat(subject, "你还记得什么吗").blockLast();

        assertThat(reply).contains("1 条确认的记忆");
        verify(quotaGuard, never()).reserve(anyLong(), any(), anyInt());
        verify(chatModelProvider, never()).getIfAvailable();
        verify(messageRepository, times(2)).append(any());
    }

    @Test
    void demoMemoryCountOnlyUsesMemoriesThatPassedTheRevocationGuard() {
        Companion companion = persistedCompanion();
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        // 记忆服务只返回通过撤权检查的可用记忆（已撤权/失效记忆不会出现在里面）。
        when(memoryService.confirmedMemoriesUsableBy(companion, subject, 100)).thenReturn(List.of());

        String reply = service.chat(subject, "你还记得什么吗").blockLast();

        assertThat(reply).contains("还没有确认的记忆");
        verify(memoryService).confirmedMemoriesUsableBy(companion, subject, 100);
        verify(chatModelProvider, never()).getIfAvailable();
    }

    @Test
    void revokedMemoryAuthorizationFailureFailsTheChatInsteadOfLeakingItToTheModel() {
        Companion companion = persistedCompanion();
        properties.setChatPolicy(CompanionFeatureProperties.CompanionChatPolicy.MODEL);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        // 授权服务无法验证某条记忆的来源图片权限：记忆守卫 fail-closed，
        // 本轮请求失败，绝不会把无法确认权限的记忆组装进上下文。
        when(memoryService.confirmedMemoriesUsableBy(companion, subject, 100))
                .thenThrow(new BusinessException(ErrorCode.OPERATION_ERROR,
                        "暂时无法验证图片访问权限，请稍后重试"));

        assertThatThrownBy(() -> service.chat(subject, "你好").blockLast())
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(ErrorCode.OPERATION_ERROR.getCode());
        verify(contextAssembler, never()).systemPrompt(anyLong(), anyLong(), anyInt(), any(), any(CompanionMemoryRecall.MemoryRecall.class), any(CompanionChatContextAssembler.Conversation.class));
        verify(chatModelProvider, never()).getIfAvailable();
        verify(memoryService).confirmedMemoriesUsableBy(companion, subject, 100);
    }

    @Test
    void demoReplyFallsBackWhenNoKeywordMatches() {
        Companion companion = persistedCompanion();
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));

        String reply = service.chat(subject, "你好呀").blockLast();

        assertThat(reply).contains("演示回复");
        assertThat(reply).contains("绫页").doesNotContain("喂", "？");
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
        when(contextAssembler.systemPrompt(eq(11L), eq(7L), eq(5), any(), any(CompanionMemoryRecall.MemoryRecall.class), any(CompanionChatContextAssembler.Conversation.class))).thenReturn("系统提示");
        // 三条历史（倒序），每条 120 码点；预算 400 只装得下最近的约两条。
        String longText = "历史消息".repeat(30);
        CompanionChatMessage latest = CompanionChatMessage.companion(companion.id(), 7L, longText,
                "internal", "demo-v1", NOW.plusSeconds(3)).withId(43L);
        CompanionChatMessage middle = CompanionChatMessage.user(companion.id(), 7L, longText, NOW.plusSeconds(2)).withId(42L);
        CompanionChatMessage oldest = CompanionChatMessage.companion(companion.id(), 7L, longText,
                "internal", "demo-v1", NOW.plusSeconds(1)).withId(41L);
        when(messageRepository.findRecent(companion.id(), 21))
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
        when(contextAssembler.systemPrompt(eq(11L), eq(7L), eq(5), any(), any(CompanionMemoryRecall.MemoryRecall.class), any(CompanionChatContextAssembler.Conversation.class))).thenReturn("系统提示");
        when(messageRepository.findRecent(companion.id(), 21))
                .thenReturn(List.of(CompanionChatMessage.companion(companion.id(), 7L,
                        "历史消息".repeat(30), "internal", "demo-v1", NOW.plusSeconds(3)).withId(43L)));

        service.chat(subject, "你好").blockLast();

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(captor.capture());
        // 极小预算下历史全部被丢弃，但系统提示与当前消息必须保留。
        List<Message> messages = captor.getValue().getInstructions();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getText()).isEqualTo("系统提示");
        assertThat(messages.get(1).getText()).isEqualTo("你好");
    }

    @Test
    void clearHistoryDeletesOnlyTheOwnCompanionsMessages() {
        when(companionRepository.findByOwnerId(7L)).thenReturn(java.util.Optional.of(persistedCompanion()));
        when(messageRepository.deleteByCompanion(11L)).thenReturn(4);

        org.assertj.core.api.Assertions.assertThat(service.clearHistory(subject)).isEqualTo(4);
        verify(messageRepository).deleteByCompanion(11L);
        // 从聊天整理出的经历一起清掉，否则"干净的上下文"里还会想起旧聊天。

        when(companionRepository.findByOwnerId(7L)).thenReturn(java.util.Optional.empty());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.clearHistory(subject))
                .hasMessageContaining("请先唤醒伙伴");
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
        when(contextAssembler.systemPrompt(eq(11L), eq(7L), eq(5), any(), any(CompanionMemoryRecall.MemoryRecall.class), any(CompanionChatContextAssembler.Conversation.class))).thenReturn("系统提示");

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
    void exhaustedPlatformDailyQuotaDoesNotBlockByokOrTouchTrialBalance() {
        properties.setChatPolicy(CompanionFeatureProperties.CompanionChatPolicy.MODEL);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(persistedCompanion()));
        when(languageRouter.decide(7L)).thenReturn(ModelRouteDecision.byok(byokConnection(), "test-only"));
        when(quotaGuard.reserve(anyLong(), any(), anyInt())).thenThrow(
                new BusinessException(ErrorCode.FORBIDDEN_ERROR, "今日伙伴对话次数已用完"));
        when(contextAssembler.systemPrompt(anyLong(), anyLong(), anyInt(), any(), any(), any())).thenReturn("系统提示");
        when(languageInvoker.stream(any(), anyList())).thenReturn(Flux.just("自费回复"));

        assertThat(service.chat(subject, "继续聊").blockLast()).isEqualTo("自费回复");
        verify(quotaGuard, never()).reserve(anyLong(), any(), anyInt());
        org.mockito.Mockito.verifyNoInteractions(trialLedger);
        verify(modelUsageService).recordSuccess(7L, ModelTask.LANGUAGE_AGENT, 9L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK);
    }

    @Test
    void exhaustedPlatformDailyQuotaDoesNotBlockDemo() {
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(persistedCompanion()));
        when(quotaGuard.reserve(anyLong(), any(), anyInt())).thenThrow(
                new BusinessException(ErrorCode.FORBIDDEN_ERROR, "今日伙伴对话次数已用完"));
        assertThat(service.chat(subject, "你好").blockLast()).contains("演示回复");
        verify(quotaGuard, never()).reserve(anyLong(), any(), anyInt());
        org.mockito.Mockito.verifyNoInteractions(trialLedger, languageInvoker);
    }

    @Test
    void exhaustedPlatformDailyQuotaStillRejectsPlatformBeforeMessageOrProvider() {
        properties.setChatPolicy(CompanionFeatureProperties.CompanionChatPolicy.MODEL);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(persistedCompanion()));
        when(quotaGuard.reserve(anyLong(), any(), anyInt())).thenThrow(
                new BusinessException(ErrorCode.FORBIDDEN_ERROR, "今日伙伴对话次数已用完"));
        assertThatThrownBy(() -> service.chat(subject, "你好"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("次数已用完");
        verify(messageRepository, never()).append(any());
        verify(chatModelProvider, never()).getIfAvailable();
        verify(trialLedger, never()).settle(anyLong(), anyLong());
    }

    @Test
    void byokMultilineReplyCompletesAndPersistsInsteadOfFailingAfterStreaming() {
        properties.setChatPolicy(CompanionFeatureProperties.CompanionChatPolicy.MODEL);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(persistedCompanion()));
        when(languageRouter.decide(7L)).thenReturn(ModelRouteDecision.byok(byokConnection(), "sk-secret"));
        when(contextAssembler.systemPrompt(anyLong(), anyLong(), anyInt(), any(), any(CompanionMemoryRecall.MemoryRecall.class), any(CompanionChatContextAssembler.Conversation.class)))
                .thenReturn("系统提示");
        when(languageInvoker.stream(any(ModelRouteDecision.class), anyList()))
                .thenReturn(Flux.just("你好呀。", "\n\n", "今天有什么想分享的？"));

        assertThat(String.join("", service.chat(subject, "你好").collectList().block()))
                .isEqualTo("你好呀。\n\n今天有什么想分享的？");
        ArgumentCaptor<CompanionChatMessage> saved = ArgumentCaptor.forClass(CompanionChatMessage.class);
        verify(messageRepository, times(2)).append(saved.capture());
        assertThat(saved.getAllValues().get(1).content()).isEqualTo("你好呀。\n\n今天有什么想分享的？");
        verify(modelUsageService).recordSuccess(7L, ModelTask.LANGUAGE_AGENT, 9L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK);
        verify(modelUsageService, never()).recordFailure(anyLong(), any(), any(), any(), anyString(), any(), anyString());
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
        when(contextAssembler.systemPrompt(eq(11L), eq(7L), eq(5), any(), any(CompanionMemoryRecall.MemoryRecall.class), any(CompanionChatContextAssembler.Conversation.class))).thenReturn("系统提示");

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
    void truncatedByokStreamKeepsUserMessageButNeverPersistsPartialReplyOrSuccess() {
        properties.setChatPolicy(CompanionFeatureProperties.CompanionChatPolicy.MODEL);
        when(languageRouter.decide(7L)).thenReturn(ModelRouteDecision.byok(byokConnection(), "sk-secret"));
        when(languageInvoker.stream(any(ModelRouteDecision.class), anyList()))
                .thenReturn(Flux.concat(Flux.just("partial"), Flux.error(new ModelInvocationException(
                        ConnectivityResult.UPSTREAM_ERROR, "model stream ended before completion"))));
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(persistedCompanion()));
        when(contextAssembler.systemPrompt(eq(11L), eq(7L), eq(5), any(),
                any(CompanionMemoryRecall.MemoryRecall.class), any(CompanionChatContextAssembler.Conversation.class)))
                .thenReturn("系统提示");
        var deltas = new java.util.ArrayList<String>();
        assertThatThrownBy(() -> service.chat(subject, "test").doOnNext(deltas::add).blockLast())
                .isInstanceOf(ModelInvocationException.class);
        assertThat(deltas).containsExactly("partial");
        verify(messageRepository, times(1)).append(any());
        verify(modelUsageService, never()).recordSuccess(anyLong(), any(), any(), any(), anyString(), any());
        verify(modelUsageService).recordFailure(7L, ModelTask.LANGUAGE_AGENT, 9L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK, ConnectivityResult.UPSTREAM_ERROR);
        verify(chatModelProvider, never()).getIfAvailable();
        org.mockito.Mockito.verifyNoInteractions(trialLedger);
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

    @Test
    void byokRemovedBetweenModeAndRoutingCannotBypassExplicitDemoPolicy() {
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(persistedCompanion()));
        org.mockito.Mockito.doReturn(com.li.lipicturecloud.domain.companion.CompanionChatMode.MODEL)
                .when(modeService).effectiveMode(7L);
        when(languageRouter.decide(7L)).thenReturn(ModelRouteDecision.platform());
        assertThatThrownBy(() -> service.chat(subject, "路由刚变化"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("平台模型未启用");
        org.mockito.Mockito.verifyNoInteractions(trialLedger, quotaGuard, languageInvoker);
        verify(messageRepository, never()).append(any());
        verify(chatModelProvider, never()).getIfAvailable();
    }

    @Test
    void platformPathReservesSettlesAndReleasesTrialBalance() {
        Companion companion = persistedCompanion();
        properties.setChatPolicy(CompanionFeatureProperties.CompanionChatPolicy.MODEL);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(contextAssembler.systemPrompt(eq(11L), eq(7L), eq(5), any(), any(CompanionMemoryRecall.MemoryRecall.class), any(CompanionChatContextAssembler.Conversation.class))).thenReturn("系统提示");
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse chunk = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(chunk));
        when(chunk.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(new AssistantMessage("伙伴的回复"));

        String reply = service.chat(subject, "你好").blockLast();

        assertThat(reply).isEqualTo("伙伴的回复");
        verify(quotaGuard).reserve(7L, java.time.LocalDate.parse("2026-08-14"), 50);
        verify(trialLedger).reserve(7L, 1L);
        verify(trialLedger).settle(7L, 1L);
        verify(trialLedger, never()).release(anyLong(), anyLong());

        // 平台流失败：释放预占，不结算。
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.error(new IllegalStateException("upstream down")));
        assertThatThrownBy(() -> service.chat(subject, "你好").blockLast())
                .isInstanceOf(IllegalStateException.class);
        verify(trialLedger).release(7L, 1L);
    }

    @Test
    void insufficientTrialBalanceFailsBeforeQuotaOrMessage() {
        Companion companion = persistedCompanion();
        properties.setChatPolicy(CompanionFeatureProperties.CompanionChatPolicy.MODEL);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(trialLedger.reserve(7L, 1L)).thenThrow(new BusinessException(
                ErrorCode.OPERATION_ERROR, "平台试用额度不足：可用 0，需要 1"));

        assertThatThrownBy(() -> service.chat(subject, "你好"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("试用额度不足");

        verify(quotaGuard, never()).reserve(anyLong(), any(), anyInt());
        verify(messageRepository, never()).append(any());
    }

    @Test
    void demoMoodReplyUsesTheDecayedCurrentMoodAndPersistsTheDecay() {
        Companion companion = persistedCompanion();
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        // 两小时前写入的旧情绪（精力 30、愉悦 20）：聊天气氛必须按"当前情绪"衰减后回答。
        CompanionMood stale = new CompanionMood(51L, companion.id(),
                bd("30.00"), bd("20.00"), bd("0.00"), bd("0.00"), bd("0.00"),
                2L, NOW.minusSeconds(2 * 3600L));
        when(moodRepository.findByCompanionId(companion.id())).thenReturn(Optional.of(stale));
        when(moodRepository.save(any(), anyLong())).thenReturn(true);

        String reply = service.chat(subject, "你现在心情怎么样").blockLast();

        assertThat(reply).contains("精力是 20");
        assertThat(reply).contains("愉悦 10");
        ArgumentCaptor<CompanionMood> saved = ArgumentCaptor.forClass(CompanionMood.class);
        verify(moodRepository).save(saved.capture(), eq(2L));
        assertThat(saved.getValue().energy()).isEqualByComparingTo("20.00");
        assertThat(saved.getValue().joy()).isEqualByComparingTo("10.00");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"现在呢", "刚喂了", "那张图有什么内容"})
    void aPictureFollowupUsesCurrentSessionUserReference(String query) {
        when(contextAssembler.systemPrompt(anyLong(), anyLong(), anyInt(), any(), any(), any())).thenReturn("绫页");
        Companion companion = persistedCompanion();
        properties.setChatPolicy(CompanionFeatureProperties.CompanionChatPolicy.MODEL);
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.empty());
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        var prior = CompanionChatMessage.user(11L, 7L, "图片101的内容", NOW.minusSeconds(20)).withId(40L);
        when(messageRepository.findRecent(11L, 21)).thenReturn(List.of(prior));
        var memory = CompanionMemory.candidate(11L, 7L, 101L, 21L, MemorySourceType.VISUAL,
                "石墙上的蓝色旗子", new BigDecimal("0.8"), NOW).withId(71L).confirm(NOW);
        when(memoryService.confirmedMemoriesUsableBy(companion, subject, 100)).thenReturn(List.of(memory));

        service.chat(subject, query).blockLast();

        var found = ArgumentCaptor.forClass(CompanionMemoryRecall.MemoryRecall.class);
        verify(contextAssembler).systemPrompt(eq(11L), eq(7L), eq(5), any(), found.capture(),
                any(CompanionChatContextAssembler.Conversation.class));
        assertThat(found.getValue().recalled()).extracting(CompanionMemoryRecall.RecalledMemory::memory)
                .containsExactly(memory);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"你好", "图片 101"})
    void modelContextReceivesDecayedMoodAndOnlyRelevantUsableConfirmedMemories(String query) {
        Companion companion = persistedCompanion();
        properties.setChatPolicy(CompanionFeatureProperties.CompanionChatPolicy.MODEL);
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse chunk = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(chunk));
        when(chunk.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(new AssistantMessage("伙伴的回复"));
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(contextAssembler.systemPrompt(anyLong(), anyLong(), anyInt(), any(), any(CompanionMemoryRecall.MemoryRecall.class), any(CompanionChatContextAssembler.Conversation.class)))
                .thenReturn("系统提示");
        CompanionMood stale = new CompanionMood(51L, companion.id(),
                bd("30.00"), bd("20.00"), bd("0.00"), bd("0.00"), bd("0.00"),
                2L, NOW.minusSeconds(2 * 3600L));
        when(moodRepository.findByCompanionId(companion.id())).thenReturn(Optional.of(stale));
        when(moodRepository.save(any(), anyLong())).thenReturn(true);
        CompanionMemory usable = CompanionMemory.candidate(companion.id(), 7L, 101L, 21L,
                MemorySourceType.VISUAL, "伙伴记得一张明亮的图片。", new BigDecimal("0.8"), NOW)
                .confirm(NOW);
        when(memoryService.confirmedMemoriesUsableBy(companion, subject, 100))
                .thenReturn(List.of(usable));

        service.chat(subject, query).blockLast();

        ArgumentCaptor<CompanionMood> moodCaptor = ArgumentCaptor.forClass(CompanionMood.class);
        ArgumentCaptor<CompanionMemoryRecall.MemoryRecall> memoryCaptor =
                ArgumentCaptor.forClass(CompanionMemoryRecall.MemoryRecall.class);
        verify(contextAssembler).systemPrompt(eq(11L), eq(7L), eq(5),
                moodCaptor.capture(), memoryCaptor.capture(), any(CompanionChatContextAssembler.Conversation.class));
        // 她知道自己记得 1 张图，但只有相关时才拿到内容。
        assertThat(memoryCaptor.getValue().pictureCount()).isEqualTo(1);
        // 上下文中的情绪是衰减后的当前值（30-2*5=20），记忆只含撤权守卫放行的确认记忆。
        assertThat(moodCaptor.getValue().energy()).isEqualByComparingTo("20.00");
        if (query.equals("你好")) {
            assertThat(memoryCaptor.getValue().recalled()).isEmpty(); // 无关的问候不召回最新图片
        } else {
            assertThat(memoryCaptor.getValue().recalled())
                    .extracting(CompanionMemoryRecall.RecalledMemory::memory).containsExactly(usable);
        }
    }

    @Test
    void modelContextExcludesOnlyItsOwnMessageByIdAcrossConcurrentWindows() {
        Companion companion = persistedCompanion();
        properties.setChatPolicy(CompanionFeatureProperties.CompanionChatPolicy.MODEL);
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse chunk = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(chunk));
        when(chunk.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(new AssistantMessage("伙伴的回复"));
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(contextAssembler.systemPrompt(anyLong(), anyLong(), anyInt(), any(), any(CompanionMemoryRecall.MemoryRecall.class), any(CompanionChatContextAssembler.Conversation.class)))
                .thenReturn("系统提示");
        // 本窗消息 id=51；并发另一窗口的消息 id=60 且时间更新（倒序时排在前面）。
        // 按"最新一条 USER"猜测会把 60 误当成本窗消息跳过；按精确 id 排除则 51 不重复、60 保留。
        CompanionChatMessage current = CompanionChatMessage.user(companion.id(), 7L, "你好", NOW)
                .withId(51L);
        CompanionChatMessage otherWindow = CompanionChatMessage.user(
                companion.id(), 7L, "另一个窗口的问题", NOW.plusSeconds(60)).withId(60L);
        when(messageRepository.findRecent(companion.id(), 21))
                .thenReturn(List.of(otherWindow, current));

        service.chat(subject, "你好").blockLast();

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(captor.capture());
        List<String> texts = captor.getValue().getInstructions().stream()
                .map(Message::getText).toList();
        // 系统提示 + 另一个窗口的 USER（作为历史一次） + 当前消息（一次）：
        // 当前消息不重复、别窗消息不丢失。
        assertThat(texts).containsExactly("系统提示", "另一个窗口的问题", "你好");
    }

    @Test
    void cancellingTheModelStreamReleasesTheTrialReservationExactlyOnceAndNeverPersistsAReply() {
        Companion companion = persistedCompanion();
        properties.setChatPolicy(CompanionFeatureProperties.CompanionChatPolicy.MODEL);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(contextAssembler.systemPrompt(eq(11L), eq(7L), eq(5), any(), any(CompanionMemoryRecall.MemoryRecall.class), any(CompanionChatContextAssembler.Conversation.class))).thenReturn("系统提示");
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        // 模型流持续生成（不完成也不失败）：模拟客户端断开后由控制器取消订阅。
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.never());

        Disposable subscription = service.chat(subject, "你好").subscribe();
        subscription.dispose();

        // 取消终态：中断的回复不得落库、不结算；平台试用预占必须恰好释放一次，
        // 避免客户端断开后试用余额被永久冻结（每日聊天次数按"中断不退还"保留）。
        verify(messageRepository, times(1)).append(any());
        verify(trialLedger).reserve(7L, 1L);
        verify(trialLedger).release(7L, 1L);
        verify(trialLedger, never()).settle(anyLong(), anyLong());
    }

    @Test
    void cancellingTheByokStreamNeverTouchesThePlatformTrialLedger() {
        Companion companion = persistedCompanion();
        properties.setChatPolicy(CompanionFeatureProperties.CompanionChatPolicy.MODEL);
        when(languageRouter.decide(7L)).thenReturn(
                ModelRouteDecision.byok(byokConnection(), "sk-secret"));
        when(languageInvoker.stream(any(ModelRouteDecision.class), anyList()))
                .thenReturn(Flux.never());
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(contextAssembler.systemPrompt(eq(11L), eq(7L), eq(5), any(), any(CompanionMemoryRecall.MemoryRecall.class), any(CompanionChatContextAssembler.Conversation.class))).thenReturn("系统提示");

        Disposable subscription = service.chat(subject, "在吗").subscribe();
        subscription.dispose();

        // BYOK 路径没有平台试用预占：取消只中断生成与落库，不产生任何 ledger/usage 副作用。
        verify(messageRepository, times(1)).append(any());
        verify(trialLedger, never()).reserve(anyLong(), anyLong());
        verify(trialLedger, never()).release(anyLong(), anyLong());
        verify(trialLedger, never()).settle(anyLong(), anyLong());
        verify(modelUsageService, never()).recordSuccess(anyLong(), any(), any(), any(), any(), any());
        verify(modelUsageService, never()).recordFailure(
                anyLong(), any(), any(), any(), any(), any(), anyString());
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void aNewSessionSendsNoOldHistoryAndConsolidatesThePreviousOne() {
        Companion companion = persistedCompanion();
        properties.setChatPolicy(CompanionFeatureProperties.CompanionChatPolicy.MODEL);
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse chunk = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(chunk));
        when(chunk.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(new AssistantMessage("伙伴的回复"));
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(contextAssembler.systemPrompt(anyLong(), anyLong(), anyInt(), any(),
                any(CompanionMemoryRecall.MemoryRecall.class), any(CompanionChatContextAssembler.Conversation.class)))
                .thenReturn("系统提示");
        // 上午聊的秋招，5 小时后再来一句：上午那段不再原样发给模型，而是先整理成经历。
        CompanionChatMessage morning = CompanionChatMessage.user(companion.id(), 7L, "秋招好焦虑",
                NOW.minus(java.time.Duration.ofHours(5))).withId(40L);
        when(messageRepository.findRecent(companion.id(), 21)).thenReturn(List.of(morning));

        service.chat(subject, "晚上吃什么").blockLast();

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(captor.capture());
        assertThat(captor.getValue().getInstructions()).extracting(Message::getText)
                .containsExactly("系统提示", "晚上吃什么");
        verify(episodes).schedule(eq(11L), eq(7L), any(), eq(NOW), eq(51L));
        ArgumentCaptor<CompanionChatContextAssembler.Conversation> conversation =
                ArgumentCaptor.forClass(CompanionChatContextAssembler.Conversation.class);
        verify(contextAssembler).systemPrompt(eq(11L), eq(7L), eq(5), any(),
                any(CompanionMemoryRecall.MemoryRecall.class), conversation.capture());
        assertThat(conversation.getValue().continuing()).isFalse();
        assertThat(conversation.getValue().previousMessageAt()).isEqualTo(morning.createdTime());
        assertThat(conversation.getValue().now()).isEqualTo(NOW);
    }

    @Test
    void historyOnlyShowsTheCurrentSession() {
        Companion companion = persistedCompanion();
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        CompanionChatMessage beforeLogin = CompanionChatMessage.user(companion.id(), 7L, "登录前", NOW.minusSeconds(600));
        CompanionChatMessage afterLogin = CompanionChatMessage.user(companion.id(), 7L, "登录后", NOW.minusSeconds(60));
        when(messageRepository.findRecent(companion.id(), 50)).thenReturn(List.of(afterLogin, beforeLogin));
        when(boundaries.lastLogin(7L)).thenReturn(Optional.of(NOW.minusSeconds(300)));

        assertThat(service.history(subject, 50).records())
                .extracting(record -> record.content()).containsExactly("登录后");

        // 没有重新登录，但已经隔了 3 小时：聊天列表也从空白开始。
        when(boundaries.lastLogin(7L)).thenReturn(Optional.empty());
        when(messageRepository.findRecent(companion.id(), 50)).thenReturn(List.of(
                CompanionChatMessage.user(companion.id(), 7L, "很久以前", NOW.minus(java.time.Duration.ofHours(3)))));
        assertThat(service.history(subject, 50).records()).isEmpty();
    }
}
