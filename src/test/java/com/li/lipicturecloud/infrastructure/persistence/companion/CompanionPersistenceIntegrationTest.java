package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
import com.li.lipicturecloud.domain.companion.CompanionSkill;
import com.li.lipicturecloud.domain.companion.FeedingContext;
import com.li.lipicturecloud.domain.companion.FeedingGrowth;
import com.li.lipicturecloud.domain.companion.FeedingRun;
import com.li.lipicturecloud.domain.companion.FeedingRunRepository;
import com.li.lipicturecloud.domain.companion.FeedingRunStatus;
import com.li.lipicturecloud.domain.companion.GrowthRecord;
import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import com.li.lipicturecloud.domain.companion.NutritionMode;
import com.li.lipicturecloud.domain.companion.PictureNutrition;
import com.li.lipicturecloud.domain.companion.TraitDelta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.IllegalTransactionStateException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CompanionPersistenceIntegrationTest {

    @Autowired
    private CompanionRepository companionRepository;

    @Autowired
    private GrowthRecordRepository growthRecordRepository;

    @Autowired
    private FeedingRunRepository feedingRunRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final CompanionBalance balance = CompanionBalance.v1();

    @Test
    void createIsIdempotentAndRevisionSaveRejectsStaleWriter() {
        Companion first = companionRepository.createIfAbsent(501L, balance);
        Companion second = companionRepository.createIfAbsent(501L, balance);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM companion WHERE userId = 501", Long.class)).isEqualTo(1L);

        FeedingGrowth growth = first.feed(
                PictureNutrition.demo(42L,
                        new TraitDelta(bd("0.6"), bd("0.4"), bd("0"), bd("0.2"), bd("0.3")),
                        Map.of(CompanionSkill.IMAGE_OBSERVATION, 18L), "演示营养"),
                new FeedingContext(false, 0L, 0L), balance);

        assertThat(companionRepository.save(growth.companionAfter(), first.revision())).isTrue();
        assertThat(companionRepository.save(growth.companionAfter(), first.revision())).isFalse();
        Companion reloaded = companionRepository.findByOwnerId(501L).orElseThrow();
        assertThat(reloaded.lifeExperience()).isEqualTo(42L);
        assertThat(reloaded.skillExperience())
                .containsEntry(CompanionSkill.IMAGE_OBSERVATION, 18L);

        Companion invalidRevision = new Companion(reloaded.id(), reloaded.ownerId(),
                reloaded.lifeExperience(), reloaded.level(), reloaded.lifeStage(), reloaded.traits(),
                reloaded.skillExperience(), reloaded.balanceVersion(), reloaded.revision() + 2L);
        assertThatThrownBy(() -> companionRepository.save(invalidRevision, reloaded.revision()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("revision");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lockedLookupRequiresAnActiveTransaction() {
        assertThatThrownBy(() -> companionRepository.findByOwnerIdForUpdate(501L))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentAwakenCreatesOneCompanion() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Companion> create = () -> {
                start.await(5, TimeUnit.SECONDS);
                return companionRepository.createIfAbsent(599L, balance);
            };
            Future<Companion> left = pool.submit(create);
            Future<Companion> right = pool.submit(create);
            start.countDown();

            assertThat(left.get(10, TimeUnit.SECONDS).id())
                    .isEqualTo(right.get(10, TimeUnit.SECONDS).id());
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM companion WHERE userId = 599", Long.class)).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
            jdbcTemplate.update("DELETE FROM companion_skill WHERE companionId IN "
                    + "(SELECT id FROM companion WHERE userId = 599)");
            jdbcTemplate.update("DELETE FROM companion WHERE userId = 599");
        }
    }

    @Test
    void feedingRunTransitionsAndGrowthSnapshotRoundTrip() throws Exception {
        Companion companion = companionRepository.createIfAbsent(502L, balance);
        Instant now = Instant.parse("2026-08-11T08:00:00Z");
        FeedingRun run = feedingRunRepository.insert(FeedingRun.processing(
                companion.id(), 502L, 102L,
                "6f26d166-0a82-4d9f-8a61-6c21cf2e59d0",
                sha256("pictureId=102"), "fef53056-2d9f-467d-9b1d-1afe9a6638fe",
                NutritionMode.DEMO_DETERMINISTIC, false, now));

        FeedingGrowth growth = companion.feed(
                PictureNutrition.demo(42L, TraitDelta.zero(),
                        Map.of(CompanionSkill.STORY_CREATION, 12L), "演示营养"),
                new FeedingContext(false, 0L, 0L), balance);
        GrowthRecord record = growthRecordRepository.append(GrowthRecord.from(
                run.id(), companion.id(), 102L, growth,
                run.nutritionMode(), run.contentUnderstood(),
                run.idempotencyKey(), run.correlationId(), now));

        assertThat(feedingRunRepository.complete(
                run.id(), run.revision(), record.id(), now.plusSeconds(1))).isTrue();
        GrowthRecord reloaded = growthRecordRepository.findByFeedingRunId(run.id()).orElseThrow();
        assertThat(reloaded.companionAfter()).isEqualTo(growth.companionAfter());
        assertThat(reloaded.skillExperienceDelta())
                .containsEntry(CompanionSkill.STORY_CREATION, 12L);
        assertThat(feedingRunRepository.findByKey(companion.id(), run.idempotencyKey()).orElseThrow().status())
                .isEqualTo(FeedingRunStatus.COMPLETED);
        assertThat(growthRecordRepository.hasFullFeed(companion.id(), 102L)).isTrue();
        assertThat(growthRecordRepository.sumLifeExperienceSince(companion.id(), now.minusSeconds(1)))
                .isEqualTo(42L);
        assertThat(growthRecordRepository.findRecent(companion.id(), 99))
                .extracting(GrowthRecord::id).containsExactly(record.id());
    }

    @Test
    void failedRunRetainsItsLastSafeFailureAcrossRestartAndCompletion() {
        Companion companion = companionRepository.createIfAbsent(503L, balance);
        Instant now = Instant.parse("2026-08-11T08:00:00Z");
        FeedingRun created = feedingRunRepository.insert(FeedingRun.processing(
                companion.id(), 503L, 103L, "feeding-failure-key-03", sha256Unchecked("pictureId=103"),
                "fef53056-2d9f-467d-9b1d-1afe9a6638fe", NutritionMode.DEMO_DETERMINISTIC, false, now));

        assertThat(feedingRunRepository.fail(created.id(), created.revision(), "NUTRITION_FAILED",
                "本次没有消化成功，图片未被消耗", now.plusSeconds(1))).isTrue();
        FeedingRun failed = feedingRunRepository.findByKey(companion.id(), created.idempotencyKey()).orElseThrow();
        assertThat(feedingRunRepository.restart(failed.id(), failed.revision(), now.plusSeconds(2))).isTrue();
        FeedingRun restarted = feedingRunRepository.findByKey(companion.id(), created.idempotencyKey()).orElseThrow();
        assertThat(restarted.attemptCount()).isEqualTo(2);
        assertThat(restarted.safeErrorCode()).isEqualTo("NUTRITION_FAILED");
        assertThat(feedingRunRepository.complete(restarted.id(), restarted.revision(), 9901L,
                now.plusSeconds(3))).isTrue();
        FeedingRun completed = feedingRunRepository.findByKey(companion.id(), created.idempotencyKey()).orElseThrow();
        assertThat(completed.status()).isEqualTo(FeedingRunStatus.COMPLETED);
        assertThat(completed.safeErrorMessage()).isEqualTo("本次没有消化成功，图片未被消耗");
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String sha256Unchecked(String value) {
        try {
            return sha256(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
