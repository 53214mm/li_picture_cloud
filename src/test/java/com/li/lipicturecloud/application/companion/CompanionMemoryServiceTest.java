package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.companion.view.CompanionMemoryListView;
import com.li.lipicturecloud.application.companion.view.CompanionMemoryView;
import com.li.lipicturecloud.config.CompanionFeatureProperties;
import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.domain.companion.CompanionMemory;
import com.li.lipicturecloud.domain.companion.CompanionMemoryRepository;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
import com.li.lipicturecloud.domain.companion.MemorySourceType;
import com.li.lipicturecloud.domain.companion.MemoryStatus;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant.PICTURE_VIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanionMemoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T08:00:00Z");
    private final AuthorizationSubject subject = AuthorizationSubject.user(7L);

    private CompanionRepository companionRepository;
    private CompanionMemoryRepository memoryRepository;
    private SpaceAuthorizationAccessService authorization;
    private CompanionMemoryService service;

    @BeforeEach
    void setUp() {
        companionRepository = mock(CompanionRepository.class);
        memoryRepository = mock(CompanionMemoryRepository.class);
        authorization = mock(SpaceAuthorizationAccessService.class);
        CompanionViewAssembler assembler = new CompanionViewAssembler(CompanionBalance.v1(),
                mock(PictureNutritionAnalyzer.class), new CompanionFeatureProperties());
        service = new CompanionMemoryService(companionRepository, memoryRepository, authorization,
                assembler, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void confirmTransitionsPendingMemoryOfTheOwnedCompanion() {
        Companion companion = persistedCompanion();
        CompanionMemory memory = candidate(companion, 31L, 101L);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(memoryRepository.findById(31L)).thenReturn(Optional.of(memory));
        when(memoryRepository.save(any(), anyLong())).thenReturn(true);

        CompanionMemoryView view = service.confirm(subject, 31L);

        assertThat(view.status()).isEqualTo("CONFIRMED");
        assertThat(view.content()).isEqualTo(memory.content());
        verify(memoryRepository).save(any(), anyLong());
    }

    @Test
    void correctRewritesContentAndKeepsOriginalCandidate() {
        Companion companion = persistedCompanion();
        CompanionMemory memory = candidate(companion, 31L, 101L);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(memoryRepository.findById(31L)).thenReturn(Optional.of(memory));
        when(memoryRepository.save(any(), anyLong())).thenReturn(true);

        CompanionMemoryView view = service.correct(subject, 31L, "伙伴重新想起：那是安静的清晨。");

        assertThat(view.status()).isEqualTo("CONFIRMED");
        assertThat(view.content()).isEqualTo("伙伴重新想起：那是安静的清晨。");
        assertThat(view.originalContent()).isEqualTo(memory.originalContent());
    }

    @Test
    void terminalMemoryRejectsConfirmWithParameterError() {
        Companion companion = persistedCompanion();
        CompanionMemory invalidated = candidate(companion, 31L, 101L).invalidate("PICTURE_UNAVAILABLE", NOW);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(memoryRepository.findById(31L)).thenReturn(Optional.of(invalidated));

        assertThatThrownBy(() -> service.confirm(subject, 31L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(ErrorCode.PARAMS_ERROR.getCode());
        verify(memoryRepository, never()).save(any(), anyLong());
    }

    @Test
    void memoryOfAnotherSubjectIsInvisible() {
        Companion companion = persistedCompanion();
        CompanionMemory memory = candidate(companion, 31L, 101L);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(memoryRepository.findById(31L)).thenReturn(Optional.of(memory));
        when(memoryRepository.findActive(companion.id(), 100)).thenReturn(List.of());

        assertThatThrownBy(() -> service.delete(AuthorizationSubject.user(999L), 31L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND_ERROR.getCode());
    }

    @Test
    void listingInvalidatesMemoriesWhoseSourcePictureWasRevoked() {
        Companion companion = persistedCompanion();
        CompanionMemory revoked = candidate(companion, 31L, 101L);
        CompanionMemory kept = candidate(companion, 32L, 202L);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(memoryRepository.findActive(companion.id(), 100)).thenReturn(List.of(revoked, kept));
        when(memoryRepository.save(any(), anyLong())).thenReturn(true);
        doThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR, "缺少权限"))
                .when(authorization).checkForUser(PICTURE_VIEW, 101L, 7L);
        // 失效传播先落库，随后的列表读取返回失效后的行。
        CompanionMemory revokedAfter = revoked.invalidate("PICTURE_UNAVAILABLE", NOW);
        when(memoryRepository.findRecent(companion.id(), 50)).thenReturn(List.of(revokedAfter, kept));

        CompanionMemoryListView list = service.memories(subject, 50);

        assertThat(list.records()).hasSize(2);
        CompanionMemoryView revokedView = list.records().stream()
                .filter(view -> view.id().equals(31L)).findFirst().orElseThrow();
        assertThat(revokedView.status()).isEqualTo("INVALIDATED");
        assertThat(revokedView.content()).isNull();
        assertThat(revokedView.invalidatedReason()).isEqualTo("PICTURE_UNAVAILABLE");
        CompanionMemoryView keptView = list.records().stream()
                .filter(view -> view.id().equals(32L)).findFirst().orElseThrow();
        assertThat(keptView.status()).isEqualTo("PENDING");
        assertThat(keptView.content()).isEqualTo(kept.content());
    }

    @Test
    void listingInvalidatesMemoriesWhoseSourcePictureNoLongerExists() {
        Companion companion = persistedCompanion();
        CompanionMemory missing = candidate(companion, 31L, 101L);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(memoryRepository.findActive(companion.id(), 100)).thenReturn(List.of(missing));
        when(memoryRepository.save(any(), anyLong())).thenReturn(true);
        doThrow(new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在"))
                .when(authorization).checkForUser(PICTURE_VIEW, 101L, 7L);
        CompanionMemory invalidated = missing.invalidate("PICTURE_UNAVAILABLE", NOW);
        when(memoryRepository.findRecent(companion.id(), 50)).thenReturn(List.of(invalidated));

        CompanionMemoryListView list = service.memories(subject, 50);

        assertThat(list.records()).hasSize(1);
        assertThat(list.records().get(0).status()).isEqualTo("INVALIDATED");
        assertThat(list.records().get(0).content()).isNull();
        assertThat(list.records().get(0).invalidatedReason()).isEqualTo("PICTURE_UNAVAILABLE");
    }

    @Test
    void listingKeepsContentWhenEverySourcePictureIsStillAuthorized() {
        Companion companion = persistedCompanion();
        CompanionMemory accessible = candidate(companion, 31L, 101L);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(memoryRepository.findActive(companion.id(), 100)).thenReturn(List.of(accessible));
        when(memoryRepository.findRecent(companion.id(), 50)).thenReturn(List.of(accessible));

        CompanionMemoryListView list = service.memories(subject, 50);

        assertThat(list.records()).hasSize(1);
        assertThat(list.records().get(0).status()).isEqualTo("PENDING");
        assertThat(list.records().get(0).content()).isEqualTo(accessible.content());
        verify(memoryRepository, never()).save(any(), anyLong());
    }

    @Test
    void authorizationInfrastructureFailureFailsTheListInsteadOfShowingContent() {
        Companion companion = persistedCompanion();
        CompanionMemory memory = candidate(companion, 31L, 101L);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(memoryRepository.findActive(companion.id(), 100)).thenReturn(List.of(memory));
        when(memoryRepository.findRecent(companion.id(), 50)).thenReturn(List.of(memory));
        doThrow(new BusinessException(ErrorCode.SYSTEM_ERROR, "授权服务数据库暂时不可用"))
                .when(authorization).checkForUser(PICTURE_VIEW, 101L, 7L);

        // fail-closed：授权无法验证时本次列表请求失败，不返回任何记忆内容，
        // 也不会把系统故障当成撤权永久落库为 INVALIDATED。
        assertThatThrownBy(() -> service.memories(subject, 50))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode(), Throwable::getMessage)
                .containsExactly(ErrorCode.OPERATION_ERROR.getCode(), "暂时无法验证图片访问权限，请稍后重试");
        verify(memoryRepository, never()).save(any(), anyLong());
    }

    @Test
    void runtimeAuthorizationFailureAlsoFailsTheListClosed() {
        Companion companion = persistedCompanion();
        CompanionMemory memory = candidate(companion, 31L, 101L);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(memoryRepository.findActive(companion.id(), 100)).thenReturn(List.of(memory));
        when(memoryRepository.findRecent(companion.id(), 50)).thenReturn(List.of(memory));
        doThrow(new IllegalStateException("authorization backend unavailable"))
                .when(authorization).checkForUser(PICTURE_VIEW, 101L, 7L);

        assertThatThrownBy(() -> service.memories(subject, 50))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode(), Throwable::getMessage)
                .containsExactly(ErrorCode.OPERATION_ERROR.getCode(), "暂时无法验证图片访问权限，请稍后重试");
        verify(memoryRepository, never()).save(any(), anyLong());
    }

    @Test
    void transitionRejectsMemoryWhoseSourcePictureWasRevoked() {
        Companion companion = persistedCompanion();
        CompanionMemory memory = candidate(companion, 31L, 101L);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(memoryRepository.findById(31L)).thenReturn(Optional.of(memory));
        doThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR, "缺少权限"))
                .when(authorization).checkForUser(PICTURE_VIEW, 101L, 7L);

        assertThatThrownBy(() -> service.confirm(subject, 31L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND_ERROR.getCode());
        verify(memoryRepository, never()).save(any(), anyLong());
    }

    @Test
    void transitionRejectsMemoryWhoseSourcePictureWasDeleted() {
        Companion companion = persistedCompanion();
        CompanionMemory memory = candidate(companion, 31L, 101L);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(memoryRepository.findById(31L)).thenReturn(Optional.of(memory));
        doThrow(new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在"))
                .when(authorization).checkForUser(PICTURE_VIEW, 101L, 7L);

        assertThatThrownBy(() -> service.dismiss(subject, 31L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND_ERROR.getCode());
        verify(memoryRepository, never()).save(any(), anyLong());
    }

    @Test
    void transitionFailsClosedWhenAuthorizationCannotVerify() {
        Companion companion = persistedCompanion();
        CompanionMemory memory = candidate(companion, 31L, 101L);
        when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
        when(memoryRepository.findById(31L)).thenReturn(Optional.of(memory));
        doThrow(new BusinessException(ErrorCode.SYSTEM_ERROR, "授权服务暂时不可用"))
                .when(authorization).checkForUser(PICTURE_VIEW, 101L, 7L);

        // 授权基础设施异常不能让状态转移继续执行：请求失败且不落库。
        assertThatThrownBy(() -> service.confirm(subject, 31L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode(), Throwable::getMessage)
                .containsExactly(ErrorCode.OPERATION_ERROR.getCode(), "暂时无法验证图片访问权限，请稍后重试");
        verify(memoryRepository, never()).save(any(), anyLong());
    }

    private Companion persistedCompanion() {
        return Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
    }

    private CompanionMemory candidate(Companion companion, long id, long pictureId) {
        return CompanionMemory.candidate(companion.id(), 7L, pictureId, id,
                MemorySourceType.VISUAL, "伙伴记得这张图片带给它的明亮感受。",
                new BigDecimal("0.84"), NOW).withId(id);
    }
}
