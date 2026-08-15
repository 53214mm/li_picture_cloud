package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CreationCandidate;
import com.li.lipicturecloud.domain.airuntime.CreationCandidateRepository;
import com.li.lipicturecloud.domain.airuntime.CreationKind;
import com.li.lipicturecloud.domain.airuntime.CreationLineageRepository;
import com.li.lipicturecloud.domain.airuntime.CreationStatus;
import com.li.lipicturecloud.domain.airuntime.CreationTask;
import com.li.lipicturecloud.domain.airuntime.CreationTaskRepository;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmojiDraftServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T08:00:00Z");
    private static final String KEY = "fef53056-2d9f-467d-9b1d-1afe9a6638fe";
    private static final AuthorizationSubject SUBJECT = AuthorizationSubject.user(7L);

    private CreationTaskRepository taskRepository;
    private CreationCandidateRepository candidateRepository;
    private CreationLineageRepository lineageRepository;
    private SpaceAuthorizationAccessService authorization;
    private LanguageRouter languageRouter;
    private LanguageModelInvoker languageInvoker;
    private PlatformTrialLedgerService trialLedger;
    @SuppressWarnings("unchecked")
    private ObjectProvider<ChatModel> chatModelProvider = mock(ObjectProvider.class);
    private ChatModel chatModel;
    private EmojiDraftService service;

    @BeforeEach
    void setUp() {
        taskRepository = mock(CreationTaskRepository.class);
        candidateRepository = mock(CreationCandidateRepository.class);
        lineageRepository = mock(CreationLineageRepository.class);
        authorization = mock(SpaceAuthorizationAccessService.class);
        com.li.lipicturecloud.repository.PictureRepository pictureRepository =
                mock(com.li.lipicturecloud.repository.PictureRepository.class);
        languageRouter = mock(LanguageRouter.class);
        languageInvoker = mock(LanguageModelInvoker.class);
        trialLedger = mock(PlatformTrialLedgerService.class);
        chatModel = mock(ChatModel.class);
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            ChatResponse response = mock(ChatResponse.class);
            Generation generation = mock(Generation.class);
            when(response.getResult()).thenReturn(generation);
            when(generation.getOutput()).thenReturn(new AssistantMessage(
                    "今天也元气满满！\n图里的风很温柔。\n想和你分享这一刻。"));
            return response;
        });
        service = new EmojiDraftService(taskRepository, candidateRepository, lineageRepository,
                new CreationServiceSupport(taskRepository, authorization, pictureRepository,
                        Clock.fixed(NOW, ZoneOffset.UTC)),
                languageRouter, languageInvoker, chatModelProvider, trialLedger,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(taskRepository.save(any(CreationTask.class), anyLong())).thenReturn(true);
        when(languageRouter.decide(7L)).thenReturn(ModelRouteDecision.platform());
    }

    private CreationTask task(CreationStatus status, long revision) {
        return new CreationTask(9L, 7L, CreationKind.EMOJI_DRAFT, List.of(102L), status,
                null, null, null, null, KEY, revision, NOW, NOW);
    }

    @Test
    void createReauthorizesAndInsertsEmojiTask() {
        when(taskRepository.insert(any(CreationTask.class))).thenAnswer(invocation ->
                invocation.<CreationTask>getArgument(0).withId(9L));

        CreationTask created = service.create(SUBJECT, List.of(102L), KEY);

        assertThat(created.kind()).isEqualTo(CreationKind.EMOJI_DRAFT);
        verify(authorization).checkForUser("picture:view", 102L, 7L);
    }

    @Test
    void emojiOperationsRejectCrossKindTasks() {
        CreationTask storyTask = new CreationTask(9L, 7L, CreationKind.STORY_DRAFT,
                List.of(102L), CreationStatus.PENDING, null, null, null, null, KEY, 0L, NOW, NOW);
        when(taskRepository.findById(9L)).thenReturn(Optional.of(storyTask));

        assertThatThrownBy(() -> service.generate(SUBJECT, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
        assertThatThrownBy(() -> service.select(SUBJECT, 9L, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
        assertThatThrownBy(() -> service.save(SUBJECT, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
        assertThatThrownBy(() -> service.candidates(SUBJECT, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void generateParsesCandidatesAndSettlesTrial() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.PENDING, 0L)));
        when(candidateRepository.appendAll(anyLong(), any(), any())).thenAnswer(invocation ->
                List.of(new CreationCandidate(null, 9L, 0, "今天也元气满满！", NOW),
                        new CreationCandidate(null, 9L, 1, "图里的风很温柔。", NOW),
                        new CreationCandidate(null, 9L, 2, "想和你分享这一刻。", NOW)));

        CreationTask result = service.generate(SUBJECT, 9L);

        assertThat(result.status()).isEqualTo(CreationStatus.AWAITING_CONFIRM);
        verify(candidateRepository).appendAll(org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.argThat(texts -> texts.size() == 3), any());
        verify(trialLedger).reserve(7L, EmojiDraftService.GENERATE_TRIAL_COST);
        verify(trialLedger).settle(7L, EmojiDraftService.GENERATE_TRIAL_COST);
        verify(lineageRepository).append(any());
    }

    @Test
    void generateFailureReleasesTrialAndFailsTheTask() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.PENDING, 0L)));
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("upstream down"));

        assertThatThrownBy(() -> service.generate(SUBJECT, 9L))
                .isInstanceOf(RuntimeException.class);
        verify(trialLedger).release(7L, EmojiDraftService.GENERATE_TRIAL_COST);
        verify(taskRepository).save(org.mockito.ArgumentMatchers.argThat(t ->
                t.status() == CreationStatus.FAILED), anyLong());
    }

    @Test
    void selectPicksACandidateAndSaveCompletesTheWork() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.AWAITING_CONFIRM, 2L)));
        when(candidateRepository.findByTaskId(9L)).thenReturn(List.of(
                new CreationCandidate(5L, 9L, 0, "今天也元气满满！", NOW),
                new CreationCandidate(6L, 9L, 1, "图里的风很温柔。", NOW)));

        CreationTask saving = service.select(SUBJECT, 9L, 1);
        assertThat(saving.status()).isEqualTo(CreationStatus.SAVING);
        assertThat(saving.draftText()).isEqualTo("图里的风很温柔。");

        when(taskRepository.findById(9L)).thenReturn(Optional.of(saving));
        CreationTask saved = service.save(SUBJECT, 9L);
        assertThat(saved.status()).isEqualTo(CreationStatus.SAVED);
        assertThat(saved.resultText()).isEqualTo("图里的风很温柔。");

        assertThatThrownBy(() -> service.select(SUBJECT, 9L, 9))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超出范围");
    }

    @Test
    void byokGenerateSkipsTrialAndUsesConnectionModel() {
        ModelRouteDecision byok = ModelRouteDecision.byok(ModelConnection.restore(5L, 7L,
                ModelProvider.DEEPSEEK, "主力", URI.create("https://api.deepseek.com/v1"),
                "deepseek-chat", 4L, true, 1L), "sk-secret");
        when(languageRouter.decide(7L)).thenReturn(byok);
        when(languageInvoker.stream(any(ModelRouteDecision.class), any()))
                .thenReturn(Flux.just("候选一\n候选二"));
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.PENDING, 0L)));
        when(candidateRepository.appendAll(anyLong(), any(), any())).thenAnswer(invocation ->
                List.of(new CreationCandidate(null, 9L, 0, "候选一", NOW),
                        new CreationCandidate(null, 9L, 1, "候选二", NOW)));

        CreationTask result = service.generate(SUBJECT, 9L);

        assertThat(result.status()).isEqualTo(CreationStatus.AWAITING_CONFIRM);
        verify(trialLedger, never()).reserve(anyLong(), anyLong());
    }
}
