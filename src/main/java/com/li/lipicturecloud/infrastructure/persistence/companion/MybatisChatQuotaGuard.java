package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.li.lipicturecloud.application.companion.ChatQuotaGuard;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.mapper.CompanionChatUsageMapper;
import com.li.lipicturecloud.model.entity.CompanionChatUsageEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Date;
import java.util.Objects;

/**
 * 用唯一日桶和数据库行锁实现跨 JVM 的伙伴对话轮次预占（模式同视觉日额度）。
 */
@Repository
public class MybatisChatQuotaGuard implements ChatQuotaGuard {

    private static final Logger log = LoggerFactory.getLogger(MybatisChatQuotaGuard.class);

    private final CompanionChatUsageMapper usageMapper;
    private final Clock clock;

    public MybatisChatQuotaGuard(CompanionChatUsageMapper usageMapper, Clock clock) {
        this.usageMapper = usageMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatQuotaReservation reserve(long subjectId, LocalDate usageDate, int dailyLimit) {
        if (subjectId <= 0) {
            throw new IllegalArgumentException("subjectId must be positive");
        }
        Objects.requireNonNull(usageDate, "usageDate");
        if (dailyLimit <= 0) {
            throw new IllegalArgumentException("dailyLimit must be positive");
        }

        CompanionChatUsageEntity bucket = usageMapper.selectBySubjectAndUsageDateForUpdate(subjectId, usageDate);
        ChatQuotaReservation reservation = bucket == null
                ? reserveFirstAttempt(subjectId, usageDate, dailyLimit)
                : reserveExistingAttempt(bucket, usageDate, dailyLimit);
        log.info("companion_chat_quota_reserved subjectId={} usageDate={} used={} limit={}",
                subjectId, usageDate, reservation.used(), dailyLimit);
        return reservation;
    }

    private ChatQuotaReservation reserveFirstAttempt(long subjectId, LocalDate usageDate, int dailyLimit) {
        Date now = Date.from(clock.instant());
        CompanionChatUsageEntity created = new CompanionChatUsageEntity();
        created.setSubjectId(subjectId);
        created.setUsageDate(usageDate);
        created.setAttempts(1);
        created.setRevision(0L);
        created.setCreateTime(now);
        created.setUpdateTime(now);
        try {
            usageMapper.insert(created);
            return new ChatQuotaReservation(usageDate, 1, dailyLimit);
        } catch (DuplicateKeyException raceWonElsewhere) {
            CompanionChatUsageEntity winner = usageMapper.selectBySubjectAndUsageDateForUpdate(subjectId, usageDate);
            if (winner == null) {
                throw new IllegalStateException("对话额度唯一键冲突后无法读取日桶", raceWonElsewhere);
            }
            return reserveExistingAttempt(winner, usageDate, dailyLimit);
        }
    }

    private ChatQuotaReservation reserveExistingAttempt(CompanionChatUsageEntity bucket, LocalDate usageDate,
                                                        int dailyLimit) {
        int used = Objects.requireNonNull(bucket.getAttempts(), "chat usage attempts");
        if (used >= dailyLimit) {
            throw exhausted();
        }
        Long revision = Objects.requireNonNull(bucket.getRevision(), "chat usage revision");
        int updated = usageMapper.incrementIfBelowLimit(
                Objects.requireNonNull(bucket.getId(), "chat usage id"), revision, dailyLimit,
                Date.from(clock.instant()));
        if (updated == 0) {
            throw exhausted();
        }
        return new ChatQuotaReservation(usageDate, Math.addExact(used, 1), dailyLimit);
    }

    private BusinessException exhausted() {
        return new BusinessException(ErrorCode.FORBIDDEN_ERROR, "今日伙伴对话次数已用完");
    }
}
