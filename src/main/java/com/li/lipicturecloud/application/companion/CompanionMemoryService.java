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
import java.util.List;
import java.util.Objects;
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
     */
    private void invalidateRevoked(Companion companion, AuthorizationSubject subject) {
        for (CompanionMemory memory : memoryRepository.findActive(companion.id(), MAX_ACTIVE_SCAN)) {
            if (memory.pictureId() == null || !pictureUnavailable(memory.pictureId(), subject.userId())) {
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

    private boolean pictureUnavailable(long pictureId, long userId) {
        try {
            authorization.checkForUser(PICTURE_VIEW, pictureId, userId);
            return false;
        } catch (BusinessException error) {
            if (error.getCode() == ErrorCode.NOT_FOUND_ERROR.getCode()
                    || error.getCode() == ErrorCode.NO_AUTH_ERROR.getCode()) {
                return true;
            }
            // 登录态或基础设施异常不当作撤权；保留记忆，等待下次读取再传播。
            return false;
        }
    }

    private CompanionMemoryView transition(AuthorizationSubject subject, long memoryId, String action,
                                           Function<CompanionMemory, CompanionMemory> operation) {
        Companion companion = requireCompanion(subject);
        CompanionMemory memory = requireOwnedMemory(companion, subject, memoryId);
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
