package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.companion.view.FeedPictureResult;
import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
import com.li.lipicturecloud.domain.companion.CompanionSkill;
import com.li.lipicturecloud.domain.companion.FeedingRun;
import com.li.lipicturecloud.domain.companion.FeedingRunRepository;
import com.li.lipicturecloud.domain.companion.FeedingRunStatus;
import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import com.li.lipicturecloud.domain.companion.NutritionMode;
import com.li.lipicturecloud.domain.companion.PictureNutrition;
import com.li.lipicturecloud.domain.companion.TraitDelta;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CompanionFeedingIntegrationTest {

    @Autowired private CompanionRepository companionRepository;
    @Autowired private FeedingRunRepository runRepository;
    @Autowired private GrowthRecordRepository growthRepository;
    @Autowired private CompanionFeedingCoordinator coordinator;
    @Autowired private CompanionLife companionLife;
    @Autowired private PictureNutritionAnalyzer analyzer;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentDistinctKeysForOnePictureBecomeFullThenRevisit() throws Exception {
        Companion companion = companionRepository.createIfAbsent(698L, CompanionBalance.v1());
        Instant now = Instant.parse("2026-08-11T08:00:00Z");
        FeedingRun leftRun = runRepository.insert(run(companion, 698L, "feed-concurrent-left",
                "11111111-1111-4111-8111-111111111111", now));
        FeedingRun rightRun = runRepository.insert(run(companion, 698L, "feed-concurrent-right",
                "22222222-2222-4222-8222-222222222222", now));
        PictureNutrition nutrition = nutrition();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<FeedPictureResult> left = pool.submit(() -> {
                start.await();
                return coordinator.complete(leftRun, nutrition);
            });
            Future<FeedPictureResult> right = pool.submit(() -> {
                start.await();
                return coordinator.complete(rightRun, nutrition);
            });
            start.countDown();

            assertThat(Set.of(left.get(10, TimeUnit.SECONDS).outcome(),
                    right.get(10, TimeUnit.SECONDS).outcome()))
                    .containsExactlyInAnyOrder("GROWN", "FAMILIARITY");
            Companion reloaded = companionRepository.findByOwnerId(698L).orElseThrow();
            assertThat(reloaded.lifeExperience()).isEqualTo(43L);
            assertThat(reloaded.skillExperience()).containsEntry(CompanionSkill.STORY_CREATION, 12L);
            assertThat(reloaded.revision()).isEqualTo(2L);
            assertThat(growthRepository.findRecent(companion.id(), 10)).hasSize(2);
        } finally {
            pool.shutdownNow();
            cleanup(companion.id());
        }
    }

    @Test
    void staleRunRevisionRollsBackSnapshotSkillsAndGrowth() {
        Companion companion = companionRepository.createIfAbsent(699L, CompanionBalance.v1());
        Instant now = Instant.parse("2026-08-11T08:00:00Z");
        FeedingRun stale = runRepository.insert(run(companion, 699L, "feed-stale-run-0001",
                "33333333-3333-4333-8333-333333333333", now));
        try {
            assertThat(runRepository.restart(stale.id(), stale.revision(), now.plusSeconds(1))).isTrue();

            assertThatThrownBy(() -> coordinator.complete(stale, nutrition()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("喂养运行状态已变化，请重试");

            Companion unchanged = companionRepository.findByOwnerId(699L).orElseThrow();
            assertThat(unchanged.lifeExperience()).isZero();
            assertThat(unchanged.revision()).isZero();
            assertThat(unchanged.skillExperience().get(CompanionSkill.STORY_CREATION)).isZero();
            assertThat(growthRepository.findRecent(companion.id(), 10)).isEmpty();
            FeedingRun current = runRepository.findByKey(companion.id(), stale.idempotencyKey()).orElseThrow();
            assertThat(current.status()).isEqualTo(FeedingRunStatus.PROCESSING);
            assertThat(current.revision()).isEqualTo(1L);
        } finally {
            cleanup(companion.id());
        }
    }

    @Test
    void staleAttemptCannotFailRunClaimedByRestart() {
        Companion companion = companionRepository.createIfAbsent(696L, CompanionBalance.v1());
        Instant now = Instant.parse("2026-08-11T08:00:00Z");
        FeedingRun stale = runRepository.insert(run(companion, 696L, "feed-stale-failure-01",
                "66666666-6666-4666-8666-666666666666", now));
        try {
            assertThat(runRepository.restart(stale.id(), stale.revision(), now.plusSeconds(1))).isTrue();

            assertThatThrownBy(() -> coordinator.fail(stale, "ANALYSIS_FAILED", "分析失败"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("喂养运行状态已变化，请重试");

            FeedingRun claimed = runRepository.findByKey(companion.id(), stale.idempotencyKey()).orElseThrow();
            assertThat(claimed.status()).isEqualTo(FeedingRunStatus.PROCESSING);
            assertThat(claimed.revision()).isEqualTo(1L);
        } finally {
            cleanup(companion.id());
        }
    }

    @Test
    void concurrentSameKeyReservationCreatesExactlyOneRun() throws Exception {
        Companion companion = companionRepository.createIfAbsent(697L, CompanionBalance.v1());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<FeedReservation> left = pool.submit(() -> reserveAfter(start, companion,
                    "44444444-4444-4444-8444-444444444444"));
            Future<FeedReservation> right = pool.submit(() -> reserveAfter(start, companion,
                    "55555555-5555-4555-8555-555555555555"));
            start.countDown();
            FeedReservation leftResult = left.get(10, TimeUnit.SECONDS);
            FeedReservation rightResult = right.get(10, TimeUnit.SECONDS);

            assertThat(Set.of(leftResult.kind(), rightResult.kind()))
                    .containsExactlyInAnyOrder(FeedReservation.Kind.STARTED, FeedReservation.Kind.IN_PROGRESS);
            assertThat(leftResult.run().id()).isEqualTo(rightResult.run().id());
            assertThat(leftResult.run().correlationId()).isEqualTo(rightResult.run().correlationId());
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM companion_feed_run
                    WHERE companionId = ? AND idempotencyKey = 'feed-same-key-0001'
                    """, Long.class, companion.id())).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
            cleanup(companion.id());
        }
    }

    private FeedReservation reserveAfter(CountDownLatch start, Companion companion, String correlation)
            throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return coordinator.reserve(companion, AuthorizationSubject.user(697L), 102L,
                "feed-same-key-0001", sha256("pictureId=102"), correlation,
                analyzer.mode(), analyzer.contentUnderstood());
    }

    private FeedingRun run(Companion companion, long subjectId, String key, String correlation, Instant now) {
        return FeedingRun.processing(companion.id(), subjectId, 102L, key, sha256("pictureId=102"),
                correlation, NutritionMode.DEMO_DETERMINISTIC, false, now);
    }

    private PictureNutrition nutrition() {
        return PictureNutrition.demo(42L,
                new TraitDelta(bd("0.60"), bd("0.40"), bd("0"), bd("0.20"), bd("0.30")),
                Map.of(CompanionSkill.STORY_CREATION, 12L), "演示营养");
    }

    private void cleanup(long companionId) {
        jdbcTemplate.update("DELETE FROM companion_growth_record WHERE companionId = ?", companionId);
        jdbcTemplate.update("DELETE FROM companion_feed_run WHERE companionId = ?", companionId);
        jdbcTemplate.update("DELETE FROM companion_skill WHERE companionId = ?", companionId);
        jdbcTemplate.update("DELETE FROM companion WHERE id = ?", companionId);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
