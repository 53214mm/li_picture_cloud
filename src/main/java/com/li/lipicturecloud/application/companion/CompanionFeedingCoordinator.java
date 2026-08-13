package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.companion.view.FeedPictureResult;
import com.li.lipicturecloud.config.CompanionFeatureProperties;
import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
import com.li.lipicturecloud.domain.companion.FeedingContext;
import com.li.lipicturecloud.domain.companion.FeedingGrowth;
import com.li.lipicturecloud.domain.companion.FeedingRun;
import com.li.lipicturecloud.domain.companion.FeedingRunRepository;
import com.li.lipicturecloud.domain.companion.FeedingRunStatus;
import com.li.lipicturecloud.domain.companion.GrowthRecord;
import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import com.li.lipicturecloud.domain.companion.NutritionMode;
import com.li.lipicturecloud.domain.companion.PictureNutrition;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Service
/**
 * 管理喂养运行的短事务协调器。
 *
 * <p>分析图片可能慢，所以 reserve / fail / complete 分为独立事务；真正改变伙伴、技能、
 * 成长记录与 run 完成状态的 {@link #complete(FeedingRun, PictureNutrition)} 则必须在同一
 * 事务中完成。</p>
 */
public class CompanionFeedingCoordinator {

    private final CompanionRepository companionRepository;
    private final GrowthRecordRepository growthRepository;
    private final FeedingRunRepository runRepository;
    private final CompanionBalance balance;
    private final CompanionViewAssembler assembler;
    private final Clock clock;
    private final CompanionFeatureProperties properties;

    public CompanionFeedingCoordinator(CompanionRepository companionRepository,
                                       GrowthRecordRepository growthRepository,
                                       FeedingRunRepository runRepository,
                                       CompanionBalance balance,
                                       CompanionViewAssembler assembler,
                                       Clock clock,
                                       CompanionFeatureProperties properties) {
        this.companionRepository = companionRepository;
        this.growthRepository = growthRepository;
        this.runRepository = runRepository;
        this.balance = balance;
        this.assembler = assembler;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FeedReservation reserve(Companion companion, AuthorizationSubject subject, long pictureId,
                                   String idempotencyKey, String fingerprint, String correlationId,
                                   NutritionMode mode, boolean contentUnderstood) {
        // “先查再插”不是原子的；唯一键冲突后重新读取，才能把并发请求收敛到同一 run。
        Optional<FeedingRun> found = runRepository.findByKey(companion.id(), idempotencyKey);
        if (found.isEmpty()) {
            FeedingRun proposed = FeedingRun.processing(companion.id(), subject.userId(), pictureId,
                    idempotencyKey, fingerprint, correlationId, mode, contentUnderstood, clock.instant());
            try {
                return FeedReservation.started(runRepository.insert(proposed));
            } catch (DuplicateKeyException race) {
                found = runRepository.findByKey(companion.id(), idempotencyKey);
                if (found.isEmpty()) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "喂养运行创建冲突，请重试");
                }
            }
        }
        return existingReservation(found.orElseThrow(), pictureId, fingerprint);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reject(FeedingRun run, String safeCode, String safeMessage) {
        transitionTerminal(run, safeCode, safeMessage, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(FeedingRun run, String safeCode, String safeMessage) {
        transitionTerminal(run, safeCode, safeMessage, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public FeedPictureResult complete(FeedingRun run, PictureNutrition nutrition) {
        Objects.requireNonNull(nutrition, "nutrition");
        // 对同一伙伴加行锁，令不同图片的并发喂养也按顺序结算每日上限与重复图片规则。
        Companion locked = companionRepository.findByOwnerIdForUpdate(run.subjectId())
                .filter(value -> Objects.equals(value.id(), run.companionId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "请先唤醒伙伴"));
        // 同一次结算只读取一次时间，避免上海零点恰好跨越时日上限与记录日期不一致。
        Instant now = clock.instant();
        boolean repeated = growthRepository.hasFullFeed(locked.id(), run.pictureId());
        long today = growthRepository.sumLifeExperienceSince(locked.id(), balance.startOfDay(now));
        long repeatTotal = growthRepository.sumRevisitExperience(locked.id(), run.pictureId());
        FeedingGrowth growth = locked.feed(nutrition, new FeedingContext(repeated, today, repeatTotal), balance);
        if (!companionRepository.save(growth.companionAfter(), locked.revision())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "伙伴状态已变化，请重试");
        }
        GrowthRecord record = growthRepository.append(GrowthRecord.from(run.id(), locked.id(), run.pictureId(),
                growth, run.nutritionMode(), run.contentUnderstood(), run.idempotencyKey(),
                run.correlationId(), now));
        // run 也要 CAS：失败会让整个事务回滚，避免伙伴成长了却没有可重放的完成回执。
        if (!runRepository.complete(run.id(), run.revision(), record.id(), now)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "喂养运行状态已变化，请重试");
        }
        return assembler.feedResult(record);
    }

    private FeedReservation existingReservation(FeedingRun run, long pictureId, String fingerprint) {
        if (run.pictureId() != pictureId || !run.requestFingerprint().equals(fingerprint)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "幂等键已用于另一张图片");
        }
        // 回放依然从已存成长记录组装，不重新分析、不重新授权成新结果，也不增加经验。
        if (run.status() == FeedingRunStatus.COMPLETED) {
            GrowthRecord record = growthRepository.findByFeedingRunId(run.id())
                    .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_ERROR, "喂养回执不完整"));
            return FeedReservation.replay(run, assembler.feedResult(record));
        }
        if (run.status() == FeedingRunStatus.REJECTED) {
            return FeedReservation.rejected(run);
        }
        Instant now = clock.instant();
        boolean restart = run.status() == FeedingRunStatus.FAILED
                || !run.updatedAt().isAfter(now.minus(properties.getProcessingTimeout()));
        if (!restart) {
            return FeedReservation.inProgress(run);
        }
        if (runRepository.restart(run.id(), run.revision(), now)) {
            return FeedReservation.started(run.restarted(now));
        }
        FeedingRun current = runRepository.findByKey(run.companionId(), run.idempotencyKey())
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_ERROR, "喂养运行状态已变化，请重试"));
        return existingReservation(current, pictureId, fingerprint);
    }

    private void transitionTerminal(FeedingRun run, String safeCode, String safeMessage, boolean reject) {
        Instant now = clock.instant();
        boolean changed = reject
                ? runRepository.reject(run.id(), run.revision(), safeCode, safeMessage, now)
                : runRepository.fail(run.id(), run.revision(), safeCode, safeMessage, now);
        if (changed) {
            return;
        }
        FeedingRun current = runRepository.findByKey(run.companionId(), run.idempotencyKey())
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_ERROR, "喂养运行状态已变化，请重试"));
        FeedingRunStatus expected = reject ? FeedingRunStatus.REJECTED : FeedingRunStatus.FAILED;
        // 旧尝试不能用新 revision 接管仍在处理的 run；只能接受“别人已完成同一终态”。
        if (current.status() != expected) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "喂养运行状态已变化，请重试");
        }
    }
}
