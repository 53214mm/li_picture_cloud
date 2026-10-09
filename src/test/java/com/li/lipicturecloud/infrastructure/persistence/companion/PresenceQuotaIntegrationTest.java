package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.li.lipicturecloud.application.companion.presence.PresenceQuotaGuard;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 用真实数据库竞争验证视觉调用额度：唯一日桶加行锁，而非依赖 JVM 内存计数。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PresenceQuotaIntegrationTest {

    private static final long SUBJECT_ID = 7_903L;
    private static final LocalDate USAGE_DATE = LocalDate.of(2026, 8, 13);

    @Autowired
    private PresenceQuotaGuard quotaGuard;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void reservationSurvivesCallerRollbackAndDoesNotConsumeChatAllowance() {
        long owner = SUBJECT_ID + 1;
        try {
            var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            assertThatThrownBy(() -> transaction.execute(status -> {
                quotaGuard.reserve(owner, USAGE_DATE, 2);
                throw new IllegalStateException("provider failed after reservation");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(jdbcTemplate.queryForObject("SELECT attempts FROM companion_presence_usage WHERE subjectId = ? AND usageDate = ?",
                    Integer.class, owner, USAGE_DATE)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM companion_chat_usage WHERE subjectId = ? AND usageDate = ?",
                    Integer.class, owner, USAGE_DATE)).isZero();
            assertThat(quotaGuard.reserve(owner,USAGE_DATE,2)).isEqualTo(2);
            assertThatThrownBy(() -> quotaGuard.reserve(owner,USAGE_DATE,2)).isInstanceOf(BusinessException.class);
            assertThat(quotaGuard.reserve(owner,USAGE_DATE.plusDays(1),2)).isEqualTo(1);
        } finally { jdbcTemplate.update("DELETE FROM companion_presence_usage WHERE subjectId = ?", owner); }
    }

    @Test
    void concurrentReservationsNeverExceedTheDailyLimit() throws Exception {
        int dailyLimit = 3;
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int index = 0; index < 10; index++) {
                attempts.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    try {
                        quotaGuard.reserve(SUBJECT_ID, USAGE_DATE, dailyLimit);
                        return true;
                    } catch (BusinessException exhausted) {
                        assertThat(exhausted.getCode()).isEqualTo(ErrorCode.FORBIDDEN_ERROR.getCode());
                        assertThat(exhausted).hasMessage("今日伙伴主动判断次数已用完");
                        return false;
                    }
                }));
            }
            start.countDown();

            long successes = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get(10, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(dailyLimit);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT attempts FROM companion_presence_usage
                    WHERE subjectId = ? AND usageDate = ?
                    """, Integer.class, SUBJECT_ID, USAGE_DATE)).isEqualTo(dailyLimit);
        } finally {
            pool.shutdownNow();
            jdbcTemplate.update("DELETE FROM companion_presence_usage WHERE subjectId = ?", SUBJECT_ID);
        }
    }

    @Test
    void validatesTheDailyLimitBeforeTouchingTheUsageBucket() {
        assertThatThrownBy(() -> quotaGuard.reserve(SUBJECT_ID, USAGE_DATE, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dailyLimit must be positive");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM companion_presence_usage
                WHERE subjectId = ? AND usageDate = ?
                """, Long.class, SUBJECT_ID, USAGE_DATE)).isZero();
    }
}
