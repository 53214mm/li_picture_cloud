package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.li.lipicturecloud.application.companion.VisionQuotaGuard;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.mapper.CompanionVisionUsageMapper;
import com.li.lipicturecloud.model.entity.CompanionVisionUsageEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.Objects;

/**
 * 用唯一日桶和数据库行锁实现跨 JVM 的视觉额度预占。
 */
@Repository
public class MybatisVisionQuotaGuard implements VisionQuotaGuard {

    private final CompanionVisionUsageMapper usageMapper;

    public MybatisVisionQuotaGuard(CompanionVisionUsageMapper usageMapper) {
        this.usageMapper = usageMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VisionQuotaReservation reserve(long subjectId, LocalDate usageDate, int dailyLimit) {
        if (subjectId <= 0) {
            throw new IllegalArgumentException("subjectId must be positive");
        }
        Objects.requireNonNull(usageDate, "usageDate");
        if (dailyLimit <= 0) {
            throw new IllegalArgumentException("dailyLimit must be positive");
        }

        CompanionVisionUsageEntity bucket = usageMapper.selectBySubjectAndUsageDateForUpdate(subjectId, usageDate);
        if (bucket == null) {
            return reserveFirstAttempt(subjectId, usageDate, dailyLimit);
        }
        return reserveExistingAttempt(bucket, usageDate, dailyLimit);
    }

    private VisionQuotaReservation reserveFirstAttempt(long subjectId, LocalDate usageDate, int dailyLimit) {
        Date now = Date.from(Instant.now());
        CompanionVisionUsageEntity created = new CompanionVisionUsageEntity();
        created.setSubjectId(subjectId);
        created.setUsageDate(usageDate);
        created.setAttempts(1);
        created.setRevision(0L);
        created.setCreateTime(now);
        created.setUpdateTime(now);
        try {
            usageMapper.insert(created);
            return new VisionQuotaReservation(usageDate, 1, dailyLimit);
        } catch (DuplicateKeyException raceWonElsewhere) {
            // 唯一键裁决并发首次预占；随后对赢家行加锁并按同一日上限继续处理。
            CompanionVisionUsageEntity winner = usageMapper.selectBySubjectAndUsageDateForUpdate(subjectId, usageDate);
            if (winner == null) {
                throw new IllegalStateException("视觉额度唯一键冲突后无法读取日桶", raceWonElsewhere);
            }
            return reserveExistingAttempt(winner, usageDate, dailyLimit);
        }
    }

    private VisionQuotaReservation reserveExistingAttempt(CompanionVisionUsageEntity bucket, LocalDate usageDate,
                                                           int dailyLimit) {
        int used = Objects.requireNonNull(bucket.getAttempts(), "vision usage attempts");
        if (used >= dailyLimit) {
            throw exhausted();
        }
        Long revision = Objects.requireNonNull(bucket.getRevision(), "vision usage revision");
        int updated = usageMapper.incrementIfBelowLimit(
                Objects.requireNonNull(bucket.getId(), "vision usage id"), revision, dailyLimit,
                Date.from(Instant.now()));
        if (updated == 0) {
            // 正确路径已有行锁；条件更新仍防御误用和数据库实现差异，且不能把它伪装成已预占。
            throw exhausted();
        }
        return new VisionQuotaReservation(usageDate, Math.addExact(used, 1), dailyLimit);
    }

    private BusinessException exhausted() {
        return new BusinessException(ErrorCode.FORBIDDEN_ERROR, "今日视觉营养额度已用完");
    }
}
