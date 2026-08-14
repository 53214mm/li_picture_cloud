package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.CreationKind;
import com.li.lipicturecloud.domain.airuntime.CreationLineage;
import com.li.lipicturecloud.domain.airuntime.CreationLineageRepository;
import com.li.lipicturecloud.domain.airuntime.CreationStatus;
import com.li.lipicturecloud.domain.airuntime.CreationTask;
import com.li.lipicturecloud.domain.airuntime.CreationTaskRepository;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoryDraftServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T08:00:00Z");
    private static final String KEY = "fef53056-2d9f-467d-9b1d-1afe9a6638fe";
    private static final AuthorizationSubject SUBJECT = AuthorizationSubject.user(7L);

    private CreationTaskRepository taskRepository;
    private CreationLineageRepository lineageRepository;
    private SpaceAuthorizationAccessService authorization;
    private com.li.lipicturecloud.repository.PictureRepository pictureRepository;
    private LanguageRouter languageRouter;
    private LanguageModelInvoker languageInvoker;
    private PlatformTrialLedgerService trialLedger;
    @SuppressWarnings("unchecked")
    private org.springframework.beans.factory.ObjectProvider<
            org.springframework.ai.chat.model.ChatModel> chatModelProvider =
            mock(org.springframework.beans.factory.ObjectProvider.class);
    private org.springframework.ai.chat.model.ChatModel chatModel;
    private StoryDraftService service;

    @BeforeEach
    void setUp() {
        taskRepository = mock(CreationTaskRepository.class);
        lineageRepository = mock(CreationLineageRepository.class);
        authorization = mock(SpaceAuthorizationAccessService.class);
        pictureRepository = mock(com.li.lipicturecloud.repository.PictureRepository.class);
        com.li.lipicturecloud.model.entity.Picture picture =
                new com.li.lipicturecloud.model.entity.Picture();
        picture.setId(102L);
        picture.setCategory("旅行");
        when(pictureRepository.findById(102L)).thenReturn(java.util.Optional.of(picture));
        when(pictureRepository.findById(103L)).thenReturn(java.util.Optional.empty());
        languageRouter = mock(LanguageRouter.class);
        languageInvoker = mock(LanguageModelInvoker.class);
        trialLedger = mock(PlatformTrialLedgerService.class);
        chatModel = mock(org.springframework.ai.chat.model.ChatModel.class);
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenAnswer(
                invocation -> {
                    org.springframework.ai.chat.model.ChatResponse response =
                            mock(org.springframework.ai.chat.model.ChatResponse.class);
                    org.springframework.ai.chat.model.Generation generation =
                            mock(org.springframework.ai.chat.model.Generation.class);
                    when(response.getResult()).thenReturn(generation);
                    when(generation.getOutput()).thenReturn(
                            new org.springframework.ai.chat.messages.AssistantMessage("生成文本"));
                    return response;
                });
        service = new StoryDraftService(taskRepository, lineageRepository, authorization,
                pictureRepository, languageRouter, languageInvoker, chatModelProvider,
                trialLedger, Clock.fixed(NOW, ZoneOffset.UTC));
        when(taskRepository.save(any(CreationTask.class), anyLong())).thenReturn(true);
        when(languageRouter.decide(7L)).thenReturn(ModelRouteDecision.platform());
        when(languageInvoker.stream(any(ModelRouteDecision.class), any()))
                .thenReturn(Flux.just("生成文本"));
    }

    private CreationTask task(CreationStatus status, long revision, String outline,
                              String draft) {
        return new CreationTask(9L, 7L, CreationKind.STORY_DRAFT, List.of(102L), status,
                outline, draft, null, null, KEY, revision, NOW, NOW);
    }

    @Test
    void createChecksAuthorizationPerPictureAndInserts() {
        when(taskRepository.insert(any(CreationTask.class))).thenAnswer(invocation ->
                invocation.<CreationTask>getArgument(0).withId(9L));

        CreationTask created = service.create(SUBJECT, List.of(102L, 103L), KEY);

        assertThat(created.id()).isEqualTo(9L);
        verify(authorization).checkForUser("picture:view", 102L, 7L);
        verify(authorization).checkForUser("picture:view", 103L, 7L);

        assertThatThrownBy(() -> service.create(SUBJECT, List.of(), KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少一张图片");
    }

    @Test
    void outlineGeneratesViaPlatformRouteAndSettlesTrial() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.PENDING, 0L, null, null)));

        CreationTask result = service.outline(SUBJECT, 9L);

        assertThat(result.status()).isEqualTo(CreationStatus.AWAITING_CONFIRM);
        assertThat(result.outlineText()).isEqualTo("生成文本");
        // 执行前重新校验授权（规格 §5），创建校验之外的第二次。
        verify(authorization).checkForUser("picture:view", 102L, 7L);
        verify(trialLedger).reserve(7L, StoryDraftService.OUTLINE_TRIAL_COST);
        verify(trialLedger).settle(7L, StoryDraftService.OUTLINE_TRIAL_COST);
        ArgumentCaptor<CreationLineage> lineage = ArgumentCaptor.forClass(CreationLineage.class);
        verify(lineageRepository).append(lineage.capture());
        assertThat(lineage.getValue().capabilityId())
                .isEqualTo(StoryDraftService.CAPABILITY_OUTLINE);
        assertThat(lineage.getValue().costSource()).isEqualTo(CostSource.PLATFORM.name());
        // 大纲提示词带有安全分类落地线索。
        ArgumentCaptor<org.springframework.ai.chat.prompt.Prompt> prompt =
                ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getInstructions().get(1).getText())
                .contains("图片分类：旅行");
    }

    @Test
    void outlineViaByokSkipsTrialAndRecordsConnectionModel() {
        ModelRouteDecision byok = ModelRouteDecision.byok(ModelConnection.restore(5L, 7L,
                ModelProvider.DEEPSEEK, "主力", URI.create("https://api.deepseek.com/v1"),
                "deepseek-chat", 4L, true, 1L), "sk-secret");
        when(languageRouter.decide(7L)).thenReturn(byok);
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.PENDING, 0L, null, null)));

        CreationTask result = service.outline(SUBJECT, 9L);

        assertThat(result.modelConnectionId()).isEqualTo(5L);
        verify(trialLedger, never()).reserve(anyLong(), anyLong());
        verify(lineageRepository).append(argThatLineage(lineage ->
                lineage.modelCode().equals("deepseek-chat")
                        && lineage.costSource().equals(CostSource.BYOK.name())));
    }

    @Test
    void outlineFailureReleasesTrialAndMarksTaskFailed() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.PENDING, 0L, null, null)));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenThrow(new RuntimeException("upstream down"));

        assertThatThrownBy(() -> service.outline(SUBJECT, 9L))
                .isInstanceOf(RuntimeException.class);
        verify(trialLedger).release(7L, StoryDraftService.OUTLINE_TRIAL_COST);
        verify(trialLedger, never()).settle(anyLong(), anyLong());
        // 任务转入 FAILED 终态。
        verify(taskRepository).save(org.mockito.ArgumentMatchers.argThat(t ->
                t.status() == CreationStatus.FAILED), anyLong());
    }

    @Test
    void insufficientTrialBalanceAlsoFailsTheTaskInsteadOfLeavingItStuck() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.PENDING, 0L, null, null)));
        when(trialLedger.reserve(7L, StoryDraftService.OUTLINE_TRIAL_COST))
                .thenThrow(new BusinessException(ErrorCode.OPERATION_ERROR, "平台试用额度不足"));

        assertThatThrownBy(() -> service.outline(SUBJECT, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("试用额度不足");
        // 任务转入 FAILED 终态，而不是停留在 OUTLINING。
        verify(taskRepository).save(org.mockito.ArgumentMatchers.argThat(t ->
                t.status() == CreationStatus.FAILED), anyLong());
    }

    @Test
    void brokenByokRouteFailsTheTaskInsteadOfLeavingItStuck() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.PENDING, 0L, null, null)));
        when(languageRouter.decide(7L)).thenThrow(new BusinessException(
                ErrorCode.OPERATION_ERROR, "语言任务路由的连接已停用，请启用或清除路由规则"));

        assertThatThrownBy(() -> service.outline(SUBJECT, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("路由的连接已停用");
        verify(taskRepository).save(org.mockito.ArgumentMatchers.argThat(t ->
                t.status() == CreationStatus.FAILED), anyLong());
    }

    @Test
    void confirmAndDraftAndSaveFollowTheStateMachine() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.AWAITING_CONFIRM, 2L, "大纲", null)));
        assertThat(service.confirmOutline(SUBJECT, 9L).status())
                .isEqualTo(CreationStatus.DRAFTING);

        // draft() 期望 AWAITING_CONFIRM 且只有大纲（用户确认大纲后进入）。
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.AWAITING_CONFIRM, 4L, "大纲", null)));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenAnswer(
                invocation -> {
                    org.springframework.ai.chat.model.ChatResponse response =
                            mock(org.springframework.ai.chat.model.ChatResponse.class);
                    org.springframework.ai.chat.model.Generation generation =
                            mock(org.springframework.ai.chat.model.Generation.class);
                    when(response.getResult()).thenReturn(generation);
                    when(generation.getOutput()).thenReturn(
                            new org.springframework.ai.chat.messages.AssistantMessage("故事草稿"));
                    return response;
                });
        CreationTask draftResult = service.draft(SUBJECT, 9L);
        assertThat(draftResult.status()).isEqualTo(CreationStatus.AWAITING_CONFIRM);
        assertThat(draftResult.draftText()).isEqualTo("故事草稿");

        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.AWAITING_CONFIRM, 5L, "大纲", "草稿")));
        CreationTask saved = service.save(SUBJECT, 9L);
        assertThat(saved.status()).isEqualTo(CreationStatus.SAVED);
        assertThat(saved.resultText()).isEqualTo("草稿");
    }

    @Test
    void foreignTasksAreRejected() {
        CreationTask foreign = new CreationTask(9L, 8L, CreationKind.STORY_DRAFT,
                List.of(102L), CreationStatus.PENDING, null, null, null, null, KEY, 0L, NOW, NOW);
        when(taskRepository.findById(9L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.outline(SUBJECT, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权操作");
    }

    @Test
    void listExpiresStaleAwaitingTasks() {
        Instant stale = NOW.minus(java.time.Duration.ofMinutes(40));
        CreationTask awaiting = new CreationTask(9L, 7L, CreationKind.STORY_DRAFT,
                List.of(102L), CreationStatus.AWAITING_CONFIRM, "大纲", null, null, null, KEY,
                2L, stale, stale);
        when(taskRepository.findBySubjectId(7L, 20)).thenReturn(List.of(awaiting));

        List<CreationTask> tasks = service.list(SUBJECT, 20);

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).status()).isEqualTo(CreationStatus.EXPIRED);
    }

    @SuppressWarnings("unchecked")
    private static CreationLineage argThatLineage(java.util.function.Predicate<CreationLineage> check) {
        return org.mockito.ArgumentMatchers.argThat(check::test);
    }
}
