package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.companion.view.CompanionMemoryListView;
import com.li.lipicturecloud.application.companion.view.CompanionMemoryView;
import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionMemory;
import com.li.lipicturecloud.domain.companion.CompanionMemoryRepository;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant.PICTURE_VIEW;

/**
 * 记忆的确认、纠正、忽略、删除与撤权失效传播。
 *
 * <p>所有操作都先校验记忆属于当前登录主体的伙伴；状态机非法转换返回参数错误；
 * 来源图片撤权或消失时在列表读取路径惰性失效，不阻塞列表返回。</p>
 */
@Service
@ConditionalOnProperty(prefix = "app.companion", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CompanionMemoryService {

    private static final Logger log = LoggerFactory.getLogger(CompanionMemoryService.class);
    private static final int MAX_ACTIVE_SCAN = 100;

    private final CompanionRepository companionRepository;
    private final CompanionMemoryRepository memoryRepository;
    private final SpaceAuthorizationAccessService authorization;
    private final CompanionViewAssembler assembler;
    private final Clock clock;

    public CompanionMemoryService(CompanionRepository companionRepository,
                                  CompanionMemoryRepository memoryRepository,
                                  SpaceAuthorizationAccessService authorization,
                                  CompanionViewAssembler assembler,
                                  Clock clock) {
        this.companionRepository = companionRepository;
        this.memoryRepository = memoryRepository;
        this.authorization = authorization;
        this.assembler = assembler;
        this.clock = clock;
    }

    @Transactional
    public CompanionMemoryListView memories(AuthorizationSubject subject, int limit) {
        Objects.requireNonNull(subject, "subject");
        Companion companion = requireCompanion(subject);
        invalidateRevoked(companion, subject);
        List<CompanionMemory> recent = memoryRepository.findRecent(companion.id(), boundedLimit(limit));
        return new CompanionMemoryListView(recent.stream().map(assembler::memory).toList());
    }

    @Transactional
    public CompanionMemoryView confirm(AuthorizationSubject subject, long memoryId) {
        return transition(subject, memoryId, "confirm", memory -> memory.confirm(now()));
    }

    @Transactional
    public CompanionMemoryView correct(AuthorizationSubject subject, long memoryId, String correctedContent) {
        Objects.requireNonNull(correctedContent, "content");
        return transition(subject, memoryId, "correct", memory -> memory.correct(correctedContent, now()));
    }

    @Transactional
    public CompanionMemoryView dismiss(AuthorizationSubject subject, long memoryId) {
        return transition(subject, memoryId, "dismiss", memory -> memory.dismiss(now()));
    }

    @Transactional
    public CompanionMemoryView delete(AuthorizationSubject subject, long memoryId) {
        return transition(subject, memoryId, "delete", memory -> memory.delete(now()));
    }

    /**
     * 读取路径上的惰性失效传播：来源图片撤权或消失的记忆转为 INVALIDATED，内容不再对外展示。
     * 同一来源图片的多条记忆只做一次授权检查。
     *
     * <p>授权服务或基础设施异常不属于撤权：整个读取请求失败并回滚本事务，既不会把记忆
     * 错误地永久标记为 INVALIDATED，也不会在无法确认权限时继续展示记忆内容。</p>
     */
    private void invalidateRevoked(Companion companion, AuthorizationSubject subject) {
        Set<Long> unavailablePictures = new HashSet<>();
        for (CompanionMemory memory : memoryRepository.findActive(companion.id(), MAX_ACTIVE_SCAN)) {
            if (memory.pictureId() == null) {
                continue;
            }
            boolean unavailable = unavailablePictures.contains(memory.pictureId());
            if (!unavailable) {
                unavailable = sourcePictureRevokedOrMissing(memory.pictureId(), subject.userId());
                if (unavailable) {
                    unavailablePictures.add(memory.pictureId());
                }
            }
            if (!unavailable) {
                continue;
            }
            CompanionMemory invalidated = memory.invalidate("PICTURE_UNAVAILABLE", now());
            if (!memoryRepository.save(invalidated, memory.revision())) {
                log.warn("companion_memory_invalidate_conflict memoryId={} companionId={}",
                        memory.id(), companion.id());
                continue;
            }
            log.info("companion_memory_invalidated subjectId={} memoryId={} pictureId={} reason=PICTURE_UNAVAILABLE",
                    subject.userId(), memory.id(), memory.pictureId());
        }
    }

    /**
     * 判断来源图片是否处于"撤权或已不存在"状态。
     *
     * <p>只有 NOT_FOUND / NO_AUTH 属于撤权语义并返回 true；其他 BusinessException 与运行时异常
     * （登录态失效、系统错误、授权服务故障、数据库错误等）一律按"暂时无法验证权限"抛出操作错误，
     * 绝不放行：既不能把基础设施故障当撤权永久落库，也不能在无法确认权限时继续展示内容
     * 或执行状态转移。错误响应与日志都不携带记忆正文、图片 URL、Token 或模型原始响应。</p>
     */
    private boolean sourcePictureRevokedOrMissing(long pictureId, long userId) {
        try {
            authorization.checkForUser(PICTURE_VIEW, pictureId, userId);
            return false;
        } catch (BusinessException error) {
            if (error.getCode() == ErrorCode.NOT_FOUND_ERROR.getCode()
                    || error.getCode() == ErrorCode.NO_AUTH_ERROR.getCode()) {
                return true;
            }
            throw authorizationUnverifiable(pictureId, userId, error.getClass());
        } catch (RuntimeException error) {
            throw authorizationUnverifiable(pictureId, userId, error.getClass());
        }
    }

    private BusinessException authorizationUnverifiable(long pictureId, long userId,
                                                        Class<? extends Throwable> exceptionType) {
        log.warn("companion_memory_authorization_unverifiable subjectId={} pictureId={} exceptionType={}",
                userId, pictureId, exceptionType.getName());
        return new BusinessException(ErrorCode.OPERATION_ERROR, "暂时无法验证图片访问权限，请稍后重试");
    }

    private CompanionMemoryView transition(AuthorizationSubject subject, long memoryId, String action,
                                           Function<CompanionMemory, CompanionMemory> operation) {
        Companion companion = requireCompanion(subject);
        CompanionMemory memory = requireOwnedMemory(companion, subject, memoryId);
        // 撤权传播覆盖转移端点：来源图片已撤权或消失时，直接拒绝操作且不返回记忆内容。
        // 状态失效仍由列表读取路径惰性传播，避免本事务内"先失效再报错"被整体回滚。
        // 授权无法验证（系统或基础设施异常）时同样拒绝操作，绝不默认放行。
        if (memory.pictureId() != null && sourcePictureRevokedOrMissing(memory.pictureId(), subject.userId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "记忆来源图片已不可用");
        }
        try {
            CompanionMemory after = operation.apply(memory);
            if (after == memory) {
                return assembler.memory(memory);
            }
            if (!memoryRepository.save(after, memory.revision())) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "记忆状态已变化，请重试");
            }
            log.info("companion_memory_action subjectId={} memoryId={} action={} status={}",
                    subject.userId(), memoryId, action, after.status().name());
            return assembler.memory(after);
        } catch (IllegalStateException | IllegalArgumentException invalid) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "记忆当前状态不允许这个操作");
        }
    }

    private Companion requireCompanion(AuthorizationSubject subject) {
        return companionRepository.findByOwnerId(subject.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "请先唤醒伙伴"));
    }

    private CompanionMemory requireOwnedMemory(Companion companion, AuthorizationSubject subject, long memoryId) {
        return memoryRepository.findById(memoryId)
                .filter(memory -> memory.companionId() == companion.id()
                        && memory.subjectId() == subject.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "记忆不存在"));
    }

    private Instant now() {
        return clock.instant();
    }

    private static int boundedLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }
}
