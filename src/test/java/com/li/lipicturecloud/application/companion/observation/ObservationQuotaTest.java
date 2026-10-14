package com.li.lipicturecloud.application.companion.observation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ObservationQuotaTest {
    ObservationQuota quota;
    @BeforeEach void setup() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE companion_observation_usage(subjectId BIGINT PRIMARY KEY,usageDate DATE NOT NULL,textAttempts INT NOT NULL DEFAULT 0,visionAttempts INT NOT NULL DEFAULT 0,leaseToken VARCHAR(64),leaseUntil TIMESTAMP,nextAt TIMESTAMP,updateTime TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        quota=new ObservationQuota(jdbc,new DataSourceTransactionManager(ds));
    }
    @Test void ownerLeaseSpansMidnightAndOldTokenCannotReleaseNewLease() {
        var at=Instant.parse("2026-10-11T23:59:59Z");
        assertThat(quota.claim(1,"a",at)).isTrue();
        assertThat(quota.reserve(1,"a",false,at)).isTrue();
        assertThat(quota.claim(1,"b",at.plusSeconds(2))).isFalse();
        quota.release(1,"a",at.plusSeconds(46),at.plusSeconds(46));
        assertThat(quota.claim(1,"b",at.plusSeconds(47))).isTrue();
        quota.release(1,"a",at.plusSeconds(48),at.plusSeconds(48));
        assertThat(quota.claim(1,"c",at.plusSeconds(49))).isFalse();
        assertThat(quota.status(1,at.plusSeconds(49)).text()).isZero();
        assertThat(quota.status(1,at.plusSeconds(49)).usageDate()).isEqualTo(java.time.LocalDate.parse("2026-10-12"));
    }
    @Test void separateCountsPersistAndReopenDoesNotRefundDispatchedCalls() {
        var at=Instant.parse("2026-10-11T12:00:00Z");
        quota.claim(1,"a",at);quota.reserve(1,"a",false,at);quota.reserve(1,"a",true,at);
        quota.release(1,"a",at,at.plusSeconds(90));
        assertThat(quota.claim(1,"b",at.plusSeconds(89))).isFalse();
        assertThat(quota.claim(1,"b",at.plusSeconds(90))).isTrue();
        assertThat(quota.status(1,at).text()).isEqualTo(1);assertThat(quota.status(1,at).vision()).isEqualTo(1);
        assertThat(quota.status(1,at).usageDate()).isEqualTo(java.time.LocalDate.parse("2026-10-11"));
        assertThat(quota.reserve(1,"wrong",false,at)).isFalse();
        assertThat(quota.status(2,at).text()).isZero();
        assertThat(quota.status(2,at).usageDate()).isEqualTo(java.time.LocalDate.parse("2026-10-11"));
    }
    @Test void explicitBudgetRejectsInsteadOfClamping() {
        for(int limit:new int[]{0,501,1000}) assertThatThrownBy(()->new ObservationContracts.OpenInput(limit,0,"NORMAL",true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new ObservationContracts.OpenInput(12,101,"NORMAL",true)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new ObservationContracts.OpenInput(500,100,"LOW",true).textLimit()).isEqualTo(500);
    }
    @Test void dispatchAfterMidnightCountsNewDayWithoutBreakingTheOwnerLease(){
        var at=Instant.parse("2026-10-11T23:59:59Z");quota.claim(1,"a",at);quota.reserve(1,"a",false,at);
        assertThat(quota.reserve(1,"a",true,at.plusSeconds(2))).isTrue();
        assertThat(quota.status(1,at.plusSeconds(2)).vision()).isEqualTo(1);assertThat(quota.status(1,at.plusSeconds(2)).text()).isZero();
        assertThat(quota.claim(1,"b",at.plusSeconds(3))).isFalse();
    }
    @Test void concurrentTabsCanAcquireOnlyOneOwnerLease() throws Exception {
        var at=Instant.parse("2026-10-11T12:00:00Z");var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        try{
            var first=pool.submit(()->{start.await();return quota.claim(9,"tab-a",at);});
            var second=pool.submit(()->{start.await();return quota.claim(9,"tab-b",at);});start.countDown();
            assertThat(List.of(first.get(5,java.util.concurrent.TimeUnit.SECONDS),second.get(5,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }finally{pool.shutdownNow();}
    }
    @Test void dispatchRequiresTwentySecondsOfOwnedLeaseAndOldTokensCannotReserve() {
        var at=Instant.parse("2026-10-11T12:00:00Z");
        assertThat(quota.claim(1,"a",at)).isTrue();
        assertThat(quota.reserve(1,"a",false,at.plusSeconds(24))).isTrue();
        assertThat(quota.reserve(1,"a",false,at.plusSeconds(25))).isFalse();
        assertThat(quota.reserve(1,"a",true,at.plusSeconds(34))).isFalse();
        assertThat(quota.status(1,at).text()).isEqualTo(1);
        assertThat(quota.status(1,at).vision()).isZero();
        assertThat(quota.claim(1,"b",at.plusSeconds(45))).isTrue();
        assertThat(quota.reserve(1,"a",false,at.plusSeconds(45))).isFalse();
        quota.release(1,"a",at.plusSeconds(46),at.plusSeconds(46));
        assertThat(quota.claim(1,"c",at.plusSeconds(46))).isFalse();
    }
    @Test void delayedAuthorizationExtendsOnlyAnUnexpiredOwnedLeaseWithoutAnotherAttempt() {
        var at=Instant.parse("2026-10-11T12:00:00Z");quota.claim(1,"a",at);
        assertThat(quota.reserve(1,"a",false,at.plusSeconds(24))).isTrue();
        quota.coverDispatch(1,"a",java.time.Clock.fixed(at.plusSeconds(31),java.time.ZoneOffset.UTC));
        assertThat(quota.claim(1,"b",at.plusSeconds(45))).isFalse();
        assertThat(quota.status(1,at).text()).isEqualTo(1);
        assertThat(quota.claim(1,"b",at.plusSeconds(51))).isTrue();
        assertThatThrownBy(()->quota.coverDispatch(1,"a",java.time.Clock.fixed(at.plusSeconds(51),java.time.ZoneOffset.UTC))).hasMessage("OWNER_BUSY_OR_INTERVAL");
        assertThatThrownBy(()->quota.coverDispatch(1,"b",java.time.Clock.fixed(at.plusSeconds(96),java.time.ZoneOffset.UTC))).hasMessage("OWNER_BUSY_OR_INTERVAL");
    }
}
