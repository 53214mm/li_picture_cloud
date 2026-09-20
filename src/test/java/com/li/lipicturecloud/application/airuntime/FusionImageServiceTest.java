package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.application.airuntime.view.FusionImageView;
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
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
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
    private FusionArtworkSaver artworkSaver;
    private ModelConnectionService connectionService;
    private FusionImageService service;

    @BeforeEach
    void setUp() {
        taskRepository = mock(CreationTaskRepository.class);
        fusionImageRepository = mock(CreationFusionImageRepository.class);
        lineageRepository = mock(CreationLineageRepository.class);
        authorization = mock(SpaceAuthorizationAccessService.class);
        com.li.lipicturecloud.repository.PictureRepository pictureRepository =
                mock(com.li.lipicturecloud.repository.PictureRepository.class);
        artworkSaver = mock(FusionArtworkSaver.class);
        connectionService = mock(ModelConnectionService.class);
        service = new FusionImageService(taskRepository, fusionImageRepository,
                lineageRepository,
                new CreationServiceSupport(taskRepository, authorization, pictureRepository,
                        Clock.fixed(NOW, ZoneOffset.UTC)),
                artworkSaver, connectionService, Clock.fixed(NOW, ZoneOffset.UTC));
        when(taskRepository.save(any(CreationTask.class), anyLong())).thenReturn(true);
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
    void generateIsExplicitlyNotOpenYetAndNeverInvokesAnyModel() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(
                task(CreationStatus.PENDING, 0L, null)));

        // 融合玩法未开放：生成入口明确失败——不调用图像模型、不预占/结算、
        // 不写暂存与血缘；任务进入安全失败终态。
        assertThatThrownBy(() -> service.generate(SUBJECT, 9L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode(), Throwable::getMessage)
                .containsExactly(ErrorCode.FORBIDDEN_ERROR.getCode(),
                        FusionImageService.NOT_OPEN_YET_MESSAGE);
        ArgumentCaptor<CreationTask> failed = ArgumentCaptor.forClass(CreationTask.class);
        verify(taskRepository).save(failed.capture(), org.mockito.ArgumentMatchers.eq(0L));
        assertThat(failed.getValue().status()).isEqualTo(CreationStatus.FAILED);
        verify(fusionImageRepository, never()).insert(any(CreationFusionImage.class));
        verify(lineageRepository, never()).append(any(CreationLineage.class));
    }

    @Test
    void generateOnTerminalFailedTaskRejectsWithoutRewritingState() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(task(CreationStatus.FAILED, 3L, null)));

        assertThatThrownBy(() -> service.generate(SUBJECT, 9L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN_ERROR.getCode());
        verify(taskRepository, never()).save(any(), anyLong());
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
        verify(taskRepository).save(failed.capture(), org.mockito.ArgumentMatchers.eq(3L));
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
