package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.li.lipicturecloud.application.companion.presence.PresenceQuotaGuard;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.mapper.CompanionPresenceUsageMapper;
import com.li.lipicturecloud.model.entity.CompanionPresenceUsageEntity;
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
 * 用唯一日桶和数据库行锁实现跨 JVM 的伙伴主动判断轮次预占（模式同视觉日额度）。
 */
@Repository
public class MybatisPresenceQuotaGuard implements PresenceQuotaGuard {

    private static final Logger log = LoggerFactory.getLogger(MybatisPresenceQuotaGuard.class);

    private final CompanionPresenceUsageMapper usageMapper;
    private final Clock clock;

    public MybatisPresenceQuotaGuard(CompanionPresenceUsageMapper usageMapper, Clock clock) {
        this.usageMapper = usageMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int reserve(long subjectId, LocalDate usageDate, int dailyLimit) {
        if (subjectId <= 0) {
            throw new IllegalArgumentException("subjectId must be positive");
        }
        Objects.requireNonNull(usageDate, "usageDate");
        if (dailyLimit <= 0) {
            throw new IllegalArgumentException("dailyLimit must be positive");
        }

        CompanionPresenceUsageEntity bucket = usageMapper.selectBySubjectAndUsageDateForUpdate(subjectId, usageDate);
        int reservation = bucket == null
                ? reserveFirstAttempt(subjectId, usageDate, dailyLimit)
                : reserveExistingAttempt(bucket, usageDate, dailyLimit);
        log.info("companion_presence_quota_reserved subjectId={} usageDate={} used={} limit={}",
                subjectId, usageDate, reservation, dailyLimit);
        return reservation;
    }

    private int reserveFirstAttempt(long subjectId, LocalDate usageDate, int dailyLimit) {
        Date now = Date.from(clock.instant());
        CompanionPresenceUsageEntity created = new CompanionPresenceUsageEntity();
        created.setSubjectId(subjectId);
        created.setUsageDate(usageDate);
        created.setAttempts(1);
        created.setRevision(0L);
        created.setCreateTime(now);
        created.setUpdateTime(now);
        try {
            usageMapper.insert(created);
            return 1;
        } catch (DuplicateKeyException raceWonElsewhere) {
            CompanionPresenceUsageEntity winner = usageMapper.selectBySubjectAndUsageDateForUpdate(subjectId, usageDate);
            if (winner == null) {
                throw new IllegalStateException("对话额度唯一键冲突后无法读取日桶", raceWonElsewhere);
            }
            return reserveExistingAttempt(winner, usageDate, dailyLimit);
        }
    }

    private int reserveExistingAttempt(CompanionPresenceUsageEntity bucket, LocalDate usageDate,
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
        return Math.addExact(used, 1);
    }

    private BusinessException exhausted() {
        return new BusinessException(ErrorCode.FORBIDDEN_ERROR, "今日伙伴主动判断次数已用完");
    }
}
