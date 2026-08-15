package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.application.airuntime.view.FusionImageView;
import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.CreationFusionImage;
import com.li.lipicturecloud.domain.airuntime.CreationFusionImageRepository;
import com.li.lipicturecloud.domain.airuntime.CreationKind;
import com.li.lipicturecloud.domain.airuntime.CreationLineage;
import com.li.lipicturecloud.domain.airuntime.CreationLineageRepository;
import com.li.lipicturecloud.domain.airuntime.CreationStatus;
import com.li.lipicturecloud.domain.airuntime.CreationTask;
import com.li.lipicturecloud.domain.airuntime.CreationTaskRepository;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FusionImageServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T08:00:00Z");
    private static final String KEY = "fef53056-2d9f-467d-9b1d-1afe9a6638fe";
    private static final AuthorizationSubject SUBJECT = AuthorizationSubject.user(7L);
    private static final String TINY_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";
    private static final ModelConnection CONNECTION = ModelConnection.restore(5L, 7L,
            ModelProvider.OPENAI, "我的图片模型", URI.create("https://api.openai.com/v1"),
            "gpt-image-2", 42L, true, 1L);

    private CreationTaskRepository taskRepository;
    private CreationFusionImageRepository fusionImageRepository;
    private CreationLineageRepository lineageRepository;
    private SpaceAuthorizationAccessService authorization;
    private com.li.lipicturecloud.repository.PictureRepository pictureRepository;
    private ImageRouter imageRouter;
    private ImageModelInvoker imageInvoker;
    private FusionArtworkSaver artworkSaver;
    private ModelUsageService usageService;
    private ModelConnectionService connectionService;
    private FusionImageService service;

    @BeforeEach
    void setUp() {
        taskRepository = mock(CreationTaskRepository.class);
        fusionImageRepository = mock(CreationFusionImageRepository.class);
        lineageRepository = mock(CreationLineageRepository.class);
        authorization = mock(SpaceAuthorizationAccessService.class);
        pictureRepository = mock(com.li.lipicturecloud.repository.PictureRepository.class);
        com.li.lipicturecloud.model.entity.Picture picture =
                new com.li.lipicturecloud.model.entity.Picture();
        picture.setId(102L);
        picture.setCategory("旅行");
        when(pictureRepository.findById(102L)).thenReturn(Optional.of(picture));
        imageRouter = mock(ImageRouter.class);
        imageInvoker = mock(ImageModelInvoker.class);
        artworkSaver = mock(FusionArtworkSaver.class);
        usageService = mock(ModelUsageService.class);
        connectionService = mock(ModelConnectionService.class);
        service = new FusionImageService(taskRepository, fusionImageRepository,
                lineageRepository,
                new CreationServiceSupport(taskRepository, authorization, pictureRepository,
                        Clock.fixed(NOW, ZoneOffset.UTC)),
                imageRouter, imageInvoker, artworkSaver, usageService, connectionService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(taskRepository.save(any(CreationTask.class), anyLong())).thenReturn(true);
        when(imageRouter.decide(7L)).thenReturn(ModelRouteDecision.byok(CONNECTION, "sk-test"));
        when(imageInvoker.invoke(any(ModelRouteDecision.class), anyString(), anyString()))
                .thenReturn(new ImageGenerationResult(null, TINY_PNG_BASE64));
        when(connectionService.findOwned(5L, 7L)).thenReturn(CONNECTION);
    }

    private CreationTask task(CreationStatus status, long revision, Long connectionId) {
        return new CreationTask(9L, 7L, CreationKind.IMAGE_FUSION, List.of(102L, 103L), status,
                null, null, null, connectionId, KEY, revision, NOW, NOW);
    }

    @Test
    void createRequiresAtLeastTwoPicturesAndInsertsFusionTask() {
        when(taskRepository.insert(any(CreationTask.class))).thenAnswer(invocation ->
                invocation.<CreationTask>getArgument(0).withId(9L));

        CreationTask created = service.create(SUBJECT, List.of(102L, 103L), KEY);

        assertThat(created.id()).isEqualTo(9L);
        assertThat(created.kind()).isEqualTo(CreationKind.IMAGE_FUSION);
        verify(authorization).checkForUser("picture:view", 102L, 7L);
        verify(authorization).checkForUser("picture:view", 103L, 7L);

        assertThatThrownBy(() -> service.create(SUBJECT, List.of(102L), KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少需要 2 张图片");
    }

    @Test
    void generateStagesBytesAndCompletesFusionAwaitingConfirmation() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.PENDING, 0L, null)));
        when(fusionImageRepository.insert(any(CreationFusionImage.class))).thenAnswer(invocation ->
                invocation.<CreationFusionImage>getArgument(0).withId(1L));

        CreationTask result = service.generate(SUBJECT, 9L);

        assertThat(result.status()).isEqualTo(CreationStatus.AWAITING_CONFIRM);
        assertThat(result.modelConnectionId()).isEqualTo(5L);
        assertThat(result.outlineText()).isNull();
        assertThat(result.draftText()).isNull();

        ArgumentCaptor<CreationFusionImage> staged =
                ArgumentCaptor.forClass(CreationFusionImage.class);
        verify(fusionImageRepository).insert(staged.capture());
        assertThat(staged.getValue().mimeType()).isEqualTo("image/png");
        assertThat(staged.getValue().bytes()).isEqualTo(Base64.getDecoder().decode(TINY_PNG_BASE64));

        ArgumentCaptor<CreationLineage> lineage =
                ArgumentCaptor.forClass(CreationLineage.class);
        verify(lineageRepository, org.mockito.Mockito.times(2)).append(lineage.capture());
        assertThat(lineage.getAllValues())
                .allSatisfy(row -> {
                    assertThat(row.capabilityId()).isEqualTo("IMAGE_FUSION_GENERATE");
                    assertThat(row.resultPictureId()).isNull();
                    assertThat(row.costSource()).isEqualTo(CostSource.BYOK.name());
                });
        verify(usageService).recordSuccess(7L, ModelTask.IMAGE_CREATION, 5L,
                ModelProvider.OPENAI, "gpt-image-2", CostSource.BYOK);
    }

    @Test
    void generateFailsLoudlyOnPlatformRouteAndMarksTaskFailed() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.PENDING, 0L, null)));
        when(imageRouter.decide(7L)).thenReturn(ModelRouteDecision.platform());

        assertThatThrownBy(() -> service.generate(SUBJECT, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("平台图片创作尚未开放");

        ArgumentCaptor<CreationTask> failed = ArgumentCaptor.forClass(CreationTask.class);
        verify(taskRepository).save(failed.capture(), eq(1L));
        assertThat(failed.getValue().status()).isEqualTo(CreationStatus.FAILED);
        verify(imageInvoker, never()).invoke(any(), anyString(), anyString());
    }

    @Test
    void generateRejectsUrlOnlyResultsAndMarksTaskFailed() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.PENDING, 0L, null)));
        when(imageInvoker.invoke(any(ModelRouteDecision.class), anyString(), anyString()))
                .thenReturn(new ImageGenerationResult(
                        URI.create("https://provider.example/fusion.png"), null));

        assertThatThrownBy(() -> service.generate(SUBJECT, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只返回图片链接");

        ArgumentCaptor<CreationTask> failed = ArgumentCaptor.forClass(CreationTask.class);
        verify(taskRepository).save(failed.capture(), eq(1L));
        assertThat(failed.getValue().status()).isEqualTo(CreationStatus.FAILED);
        verify(fusionImageRepository, never()).insert(any(CreationFusionImage.class));
    }

    @Test
    void generateRecordsUsageFailureOnInvocationError() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.PENDING, 0L, null)));
        when(imageInvoker.invoke(any(ModelRouteDecision.class), anyString(), anyString()))
                .thenThrow(new ModelInvocationException(
                        ConnectivityResult.CREDENTIAL_REJECTED, "credential rejected"));

        assertThatThrownBy(() -> service.generate(SUBJECT, 9L))
                .isInstanceOf(ModelInvocationException.class);

        verify(usageService).recordFailure(7L, ModelTask.IMAGE_CREATION, 5L,
                ModelProvider.OPENAI, "gpt-image-2", CostSource.BYOK,
                ConnectivityResult.CREDENTIAL_REJECTED);
        ArgumentCaptor<CreationTask> failed = ArgumentCaptor.forClass(CreationTask.class);
        verify(taskRepository).save(failed.capture(), eq(1L));
        assertThat(failed.getValue().status()).isEqualTo(CreationStatus.FAILED);
    }

    @Test
    void saveUploadsStagedBytesAndCompletesWithResultPictureId() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.AWAITING_CONFIRM, 2L, 5L)));
        when(fusionImageRepository.findByTaskId(9L)).thenReturn(Optional.of(
                CreationFusionImage.create(9L, "image/png",
                        Base64.getDecoder().decode(TINY_PNG_BASE64), NOW)));
        when(artworkSaver.save(any(FusionArtworkSaveRequest.class))).thenReturn(300L);

        CreationTask result = service.save(SUBJECT, 9L, 100L, "周末回忆");

        assertThat(result.status()).isEqualTo(CreationStatus.SAVED);
        assertThat(result.resultText()).isEqualTo("300");
        assertThat(result.isTerminal()).isTrue();

        ArgumentCaptor<FusionArtworkSaveRequest> request =
                ArgumentCaptor.forClass(FusionArtworkSaveRequest.class);
        verify(artworkSaver).save(request.capture());
        assertThat(request.getValue().spaceId()).isEqualTo(100L);
        assertThat(request.getValue().name()).isEqualTo("周末回忆");
        assertThat(request.getValue().mimeType()).isEqualTo("image/png");

        ArgumentCaptor<CreationLineage> lineage =
                ArgumentCaptor.forClass(CreationLineage.class);
        verify(lineageRepository, org.mockito.Mockito.times(2)).append(lineage.capture());
        assertThat(lineage.getAllValues())
                .allSatisfy(row -> {
                    assertThat(row.capabilityId()).isEqualTo("IMAGE_FUSION_SAVE");
                    assertThat(row.resultPictureId()).isEqualTo(300L);
                });
        verify(authorization).checkForUser("picture:view", 102L, 7L);
        verify(authorization).checkForUser("picture:view", 103L, 7L);
    }

    @Test
    void saveRequiresExplicitTargetSpace() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.AWAITING_CONFIRM, 2L, 5L)));

        assertThatThrownBy(() -> service.save(SUBJECT, 9L, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标空间");
        assertThatThrownBy(() -> service.save(SUBJECT, 9L, 0L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标空间");
        verify(artworkSaver, never()).save(any(FusionArtworkSaveRequest.class));
    }

    @Test
    void saveFailsWhenStagedBytesAreMissing() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.AWAITING_CONFIRM, 2L, 5L)));
        when(fusionImageRepository.findByTaskId(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(SUBJECT, 9L, 100L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("融合结果已失效");

        ArgumentCaptor<CreationTask> failed = ArgumentCaptor.forClass(CreationTask.class);
        verify(taskRepository).save(failed.capture(), eq(3L));
        assertThat(failed.getValue().status()).isEqualTo(CreationStatus.FAILED);
    }

    @Test
    void fusionEndpointsRejectNonFusionTasks() {
        CreationTask storyTask = new CreationTask(9L, 7L, CreationKind.STORY_DRAFT,
                List.of(102L), CreationStatus.PENDING, null, null, null, null, KEY, 0L, NOW, NOW);
        when(taskRepository.findById(9L)).thenReturn(Optional.of(storyTask));

        assertThatThrownBy(() -> service.generate(SUBJECT, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
        assertThatThrownBy(() -> service.previewImage(SUBJECT, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void previewReturnsStagedBytesDefensively() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.AWAITING_CONFIRM, 2L, 5L)));
        when(fusionImageRepository.findByTaskId(9L)).thenReturn(Optional.of(
                CreationFusionImage.create(9L, "image/webp",
                        new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}, NOW)));

        FusionImageView view = service.previewImage(SUBJECT, 9L);

        assertThat(view.mimeType()).isEqualTo("image/webp");
        assertThat(view.bytes()).hasSize(12);
        view.bytes()[0] = 9;
        assertThat(service.previewImage(SUBJECT, 9L).bytes()[0]).isEqualTo((byte) 1);

        when(fusionImageRepository.findByTaskId(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.previewImage(SUBJECT, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("融合结果不存在");
    }

    @Test
    void listQueriesByKindInTheRepository() {
        CreationTask fusion = task(CreationStatus.SAVED, 4L, 5L);
        when(taskRepository.findBySubjectIdAndKind(7L, CreationKind.IMAGE_FUSION, 20))
                .thenReturn(List.of(fusion));

        List<CreationTask> tasks = service.list(SUBJECT, 20);

        assertThat(tasks).containsExactly(fusion);
        verify(taskRepository).findBySubjectIdAndKind(7L, CreationKind.IMAGE_FUSION, 20);
    }

    @Test
    void generateMarksTaskFailedWhenLineageFailsAfterCompleteFusion() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.PENDING, 0L, null)));
        when(fusionImageRepository.insert(any(CreationFusionImage.class))).thenAnswer(invocation ->
                invocation.<CreationFusionImage>getArgument(0).withId(1L));
        when(lineageRepository.append(any(CreationLineage.class)))
                .thenThrow(new RuntimeException("lineage db down"));

        assertThatThrownBy(() -> service.generate(SUBJECT, 9L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("lineage db down");

        // 失败必须基于 completeFusion 之后的最新状态（AWAITING_CONFIRM，revision 2）写 FAILED。
        ArgumentCaptor<CreationTask> failed = ArgumentCaptor.forClass(CreationTask.class);
        verify(taskRepository).save(failed.capture(), eq(2L));
        assertThat(failed.getValue().status()).isEqualTo(CreationStatus.FAILED);
        // 模型调用已成功，不得记失败用量。
        verify(usageService).recordSuccess(7L, ModelTask.IMAGE_CREATION, 5L,
                ModelProvider.OPENAI, "gpt-image-2", CostSource.BYOK);
        verify(usageService, never()).recordFailure(anyLong(), any(), anyLong(), any(), anyString(),
                any(), anyString());
    }

    @Test
    void generateRejectsUnsupportedImageFormatAndMarksTaskFailed() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.PENDING, 0L, null)));
        when(imageInvoker.invoke(any(ModelRouteDecision.class), anyString(), anyString()))
                .thenReturn(new ImageGenerationResult(null,
                        Base64.getEncoder().encodeToString(new byte[24])));

        assertThatThrownBy(() -> service.generate(SUBJECT, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("格式不受支持");

        ArgumentCaptor<CreationTask> failed = ArgumentCaptor.forClass(CreationTask.class);
        verify(taskRepository).save(failed.capture(), eq(1L));
        assertThat(failed.getValue().status()).isEqualTo(CreationStatus.FAILED);
        verify(fusionImageRepository, never()).insert(any(CreationFusionImage.class));
    }

    @Test
    void saveSucceedsEvenWhenLineageAppendFailsAfterTerminalTransition() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.AWAITING_CONFIRM, 2L, 5L)));
        when(fusionImageRepository.findByTaskId(9L)).thenReturn(Optional.of(
                CreationFusionImage.create(9L, "image/png",
                        Base64.getDecoder().decode(TINY_PNG_BASE64), NOW)));
        when(artworkSaver.save(any(FusionArtworkSaveRequest.class))).thenReturn(300L);
        when(lineageRepository.append(any(CreationLineage.class)))
                .thenThrow(new RuntimeException("lineage db down"));

        // 作品已回库且任务已 SAVED：血缘追加失败只告警，绝不把成功保存报成失败。
        CreationTask result = service.save(SUBJECT, 9L, 100L, null);

        assertThat(result.status()).isEqualTo(CreationStatus.SAVED);
        assertThat(result.resultText()).isEqualTo("300");
        verify(lineageRepository).append(any(CreationLineage.class));
    }

    @Test
    void previewRefusesFailedAndExpiredTasks() {
        CreationTask failed = new CreationTask(9L, 7L, CreationKind.IMAGE_FUSION,
                List.of(102L, 103L), CreationStatus.FAILED, null, null, null, 5L, KEY,
                3L, NOW, NOW);
        when(taskRepository.findById(9L)).thenReturn(Optional.of(failed));
        when(fusionImageRepository.findByTaskId(9L)).thenReturn(Optional.of(
                CreationFusionImage.create(9L, "image/png",
                        Base64.getDecoder().decode(TINY_PNG_BASE64), NOW)));

        assertThatThrownBy(() -> service.previewImage(SUBJECT, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("融合结果不存在");
    }
}
