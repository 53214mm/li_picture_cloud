package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.li.lipicturecloud.application.companion.ChatQuotaGuard;
import com.li.lipicturecloud.domain.companion.CompanionChatMessage;
import com.li.lipicturecloud.domain.companion.CompanionChatMessageRepository;
import com.li.lipicturecloud.domain.companion.CompanionChatRole;
import com.li.lipicturecloud.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CompanionChatPersistenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-14T08:00:00Z");

    @Autowired
    private CompanionChatMessageRepository messageRepository;

    @Autowired
    private ChatQuotaGuard quotaGuard;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void messagesAppendAndListInReverseChronologicalOrder() {
        CompanionChatMessage first = messageRepository.append(
                CompanionChatMessage.user(701L, 7L, "第一句", NOW));
        CompanionChatMessage second = messageRepository.append(
                CompanionChatMessage.companion(701L, 7L, "第二句", "internal", "demo-v1", NOW.plusSeconds(1)));

        assertThat(first.id()).isPositive();
        assertThat(second.id()).isPositive();

        List<CompanionChatMessage> recent = messageRepository.findRecent(701L, 10);
        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).id()).isEqualTo(second.id());
        assertThat(recent.get(0).role()).isEqualTo(CompanionChatRole.COMPANION);
        assertThat(recent.get(1).content()).isEqualTo("第一句");
    }

    @Test
    void concurrentQuotaReservationsNeverExceedDailyLimit() throws Exception {
        int limit = 3;
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            results.add(pool.submit((Callable<Boolean>) () -> {
                start.await();
                try {
                    quotaGuard.reserve(702L, LocalDate.parse("2026-08-14"), limit);
                    return true;
                } catch (BusinessException exhausted) {
                    return false;
                }
            }));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        long accepted = results.stream().map(future -> {
            try {
                return future.get();
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        }).filter(Boolean::booleanValue).count();
        assertThat(accepted).isEqualTo(limit);
        Integer attempts = jdbcTemplate.queryForObject(
                "SELECT attempts FROM companion_chat_usage WHERE subjectId = 702 AND usageDate = '2026-08-14'",
                Integer.class);
        assertThat(attempts).isEqualTo(limit);
    }

    @Test
    void quotaExhaustionThrowsBusinessError() {
        quotaGuard.reserve(703L, LocalDate.parse("2026-08-14"), 1);

        try {
            quotaGuard.reserve(703L, LocalDate.parse("2026-08-14"), 1);
            throw new AssertionError("second reservation should be exhausted");
        } catch (BusinessException error) {
            assertThat(error.getMessage()).isEqualTo("今日伙伴对话次数已用完");
        }
    }
}
