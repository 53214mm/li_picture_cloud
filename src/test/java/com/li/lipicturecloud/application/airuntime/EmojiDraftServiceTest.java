package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CreationCandidate;
import com.li.lipicturecloud.domain.airuntime.CreationCandidateRepository;
import com.li.lipicturecloud.domain.airuntime.CreationKind;
import com.li.lipicturecloud.domain.airuntime.CreationStatus;
import com.li.lipicturecloud.domain.airuntime.CreationTask;
import com.li.lipicturecloud.domain.airuntime.CreationTaskRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    private SpaceAuthorizationAccessService authorization;
    private EmojiDraftService service;

    @BeforeEach
    void setUp() {
        taskRepository = mock(CreationTaskRepository.class);
        candidateRepository = mock(CreationCandidateRepository.class);
        authorization = mock(SpaceAuthorizationAccessService.class);
        com.li.lipicturecloud.repository.PictureRepository pictureRepository =
                mock(com.li.lipicturecloud.repository.PictureRepository.class);
        service = new EmojiDraftService(taskRepository, candidateRepository,
                new CreationServiceSupport(taskRepository, authorization, pictureRepository,
                        Clock.fixed(NOW, ZoneOffset.UTC)),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(taskRepository.save(any(CreationTask.class), anyLong())).thenReturn(true);
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
    void generateIsExplicitlyNotOpenYetAndNeverTouchesAnyModelOrCandidates() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(task(CreationStatus.PENDING, 0L)));

        assertThatThrownBy(() -> service.generate(SUBJECT, 9L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode(), Throwable::getMessage)
                .containsExactly(ErrorCode.FORBIDDEN_ERROR.getCode(),
                        EmojiDraftService.NOT_OPEN_YET_MESSAGE);
        // 零模型调用、零候选生成、零成本：任务进入安全失败终态。
        verify(candidateRepository, never()).appendAll(anyLong(), any(), any());
        verify(taskRepository).save(org.mockito.ArgumentMatchers.argThat(t ->
                t.status() == CreationStatus.FAILED), anyLong());
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
    void generateOnTerminalFailedTaskRejectsInsteadOfFailingTwice() {
        when(taskRepository.findById(9L)).thenReturn(Optional.of(task(CreationStatus.FAILED, 2L)));

        assertThatThrownBy(() -> service.generate(SUBJECT, 9L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN_ERROR.getCode());
        // 已终态不重复写 FAILED。
        verify(taskRepository, never()).save(any(), anyLong());
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
}
