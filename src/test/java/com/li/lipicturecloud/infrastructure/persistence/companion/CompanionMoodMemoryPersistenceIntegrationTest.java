package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.li.lipicturecloud.domain.companion.CompanionMemory;
import com.li.lipicturecloud.domain.companion.CompanionMemoryRepository;
import com.li.lipicturecloud.domain.companion.CompanionMood;
import com.li.lipicturecloud.domain.companion.CompanionMoodRepository;
import com.li.lipicturecloud.domain.companion.CompanionMoodRules;
import com.li.lipicturecloud.domain.companion.CompanionRelationship;
import com.li.lipicturecloud.domain.companion.CompanionRelationshipRepository;
import com.li.lipicturecloud.domain.companion.CompanionRelationshipRules;
import com.li.lipicturecloud.domain.companion.MemorySourceType;
import com.li.lipicturecloud.domain.companion.MemoryStatus;
import com.li.lipicturecloud.domain.companion.MoodImpact;
import org.junit.jupiter.api.Test;
import com.li.lipicturecloud.application.companion.CompanionMemoryService;
import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import java.time.Clock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import static com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant.PICTURE_VIEW;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CompanionMoodMemoryPersistenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-14T08:00:00Z");

    @Autowired
    private CompanionMoodRepository moodRepository;

    @Autowired
    private CompanionRelationshipRepository relationshipRepository;

    @Autowired
    private CompanionMemoryRepository memoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final CompanionMoodRules moodRules = CompanionMoodRules.v1();
    private final CompanionRelationshipRules relationshipRules = CompanionRelationshipRules.v1();

    @Test
    void moodInsertSaveAndStaleRevisionConflict() {
        CompanionMood inserted = moodRepository.insert(CompanionMood.neutral(601L, NOW));
        CompanionMood reloaded = moodRepository.findByCompanionId(601L).orElseThrow();

        assertThat(reloaded.id()).isEqualTo(inserted.id());
        assertThat(reloaded.energy()).isEqualByComparingTo("0.00");

        CompanionMood applied = reloaded.apply(new MoodImpact(
                bd("10.00"), bd("5.00"), bd("0.00"), bd("2.00"), bd("0.00")), NOW.plusSeconds(1), moodRules);
        assertThat(moodRepository.save(applied, reloaded.revision())).isTrue();
        assertThat(moodRepository.save(applied, reloaded.revision())).isFalse();

        CompanionMood after = moodRepository.findByCompanionId(601L).orElseThrow();
        assertThat(after.energy()).isEqualByComparingTo("10.00");
        assertThat(after.revision()).isEqualTo(1L);
        assertThatThrownBy(() -> moodRepository.save(after, after.revision()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("revision");
    }

    @Test
    void relationshipCreateIsIdempotentAndCapsByUniqueKey() {
        CompanionRelationship first = relationshipRepository.createIfAbsent(601L, 7L);
        CompanionRelationship second = relationshipRepository.createIfAbsent(601L, 7L);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM companion_relationship WHERE companionId = 601 AND subjectId = 7",
                Long.class)).isEqualTo(1L);

        CompanionRelationship applied = first.apply(relationshipRules.fullFeedImpact(), relationshipRules);
        assertThat(relationshipRepository.save(applied, first.revision())).isTrue();
        CompanionRelationship after = relationshipRepository.findByCompanionAndSubject(601L, 7L).orElseThrow();
        assertThat(after.familiarity()).isEqualByComparingTo("5.00");
        assertThat(after.trust()).isEqualByComparingTo("2.00");
    }

    @Test
    void memoryAppendFindAndCasTransition() {
        CompanionMemory appended = memoryRepository.append(CompanionMemory.candidate(
                601L, 7L, 101L, 31L, MemorySourceType.VISUAL,
                "伙伴记得这张图片带给它的明亮感受。", new BigDecimal("0.84"), NOW));

        assertThat(appended.id()).isPositive();
        assertThat(memoryRepository.findById(appended.id()).orElseThrow().status())
                .isEqualTo(MemoryStatus.PENDING);

        CompanionMemory confirmed = appended.confirm(NOW.plusSeconds(60));
        assertThat(memoryRepository.save(confirmed, appended.revision())).isTrue();
        assertThat(memoryRepository.save(confirmed, appended.revision())).isFalse();

        CompanionMemory after = memoryRepository.findById(appended.id()).orElseThrow();
        assertThat(after.status()).isEqualTo(MemoryStatus.CONFIRMED);
        assertThat(after.revision()).isEqualTo(1L);
        assertThat(after.content()).isEqualTo(appended.content());
    }

    @Test
    void memoryListsRespectStatusFilterAndOrder() {
        memoryRepository.append(CompanionMemory.candidate(
                602L, 7L, 101L, 41L, MemorySourceType.VISUAL,
                "伙伴记得的第一张图片感受。", new BigDecimal("0.60"), NOW));
        CompanionMemory second = memoryRepository.append(CompanionMemory.candidate(
                602L, 7L, 202L, 42L, MemorySourceType.VISUAL,
                "伙伴记得的第二张图片感受。", new BigDecimal("0.70"), NOW.plusSeconds(60)));
        memoryRepository.save(second.invalidate("PICTURE_UNAVAILABLE", NOW.plusSeconds(120)), second.revision());

        List<CompanionMemory> recent = memoryRepository.findRecent(602L, 10);
        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).id()).isEqualTo(second.id());

        List<CompanionMemory> active = memoryRepository.findActive(602L, 10);
        assertThat(active).hasSize(1);
        assertThat(active.get(0).status()).isEqualTo(MemoryStatus.PENDING);

        // 删除是终态且从列表消失；失效记忆仍保留在列表中。
        CompanionMemory pending = recent.get(1);
        memoryRepository.save(pending.delete(NOW.plusSeconds(180)), pending.revision());
        assertThat(memoryRepository.findRecent(602L, 10)).hasSize(1);
        assertThat(memoryRepository.findRecent(602L, 10).get(0).status())
                .isEqualTo(MemoryStatus.INVALIDATED);
    }

    @Test
    void confirmedRecallSurvivesOverOneHundredNewerNonConfirmedRows() {
        Companion companion = Companion.awaken(7L, CompanionBalance.v1()).persistedAs(603L);
        CompanionMemory older = memoryRepository.append(CompanionMemory.candidate(
                603L, 7L, 101L, 500L, MemorySourceType.VISUAL,
                "较早确认的图片记忆。", bd("0.8"), NOW).confirm(NOW));
        for (int i = 0; i < 156; i++) {
            CompanionMemory newer = CompanionMemory.candidate(603L, 7L, 202L, 501L + i,
                    MemorySourceType.VISUAL, "较新的未确认图片记忆。", bd("0.7"), NOW.plusSeconds(i + 1));
            if (i % 3 == 1) newer = newer.dismiss(NOW.plusSeconds(i + 1));
            if (i % 3 == 2) newer = newer.invalidate("PICTURE_UNAVAILABLE", NOW.plusSeconds(i + 1));
            memoryRepository.append(newer);
        }
        SpaceAuthorizationAccessService authorization = mock(SpaceAuthorizationAccessService.class);
        CompanionMemoryService service = new CompanionMemoryService(mock(CompanionRepository.class),
                memoryRepository, authorization, null, Clock.systemUTC());

        assertThat(service.confirmedMemoriesUsableBy(companion, AuthorizationSubject.user(7L), 100))
                .extracting(CompanionMemory::id).containsExactly(older.id());
        verify(authorization).checkForUser(PICTURE_VIEW, 101L, 7L);
        // 104 newer active rows push this actual candidate outside the old 100-row scan.
        assertThat(memoryRepository.findActive(603L, 100))
                .extracting(CompanionMemory::id).doesNotContain(older.id());
        doThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR, "来源已撤权"))
                .when(authorization).checkForUser(PICTURE_VIEW, 101L, 7L);
        assertThat(service.confirmedMemoriesUsableBy(companion, AuthorizationSubject.user(7L), 100)).isEmpty();
        assertThat(memoryRepository.findById(older.id()).orElseThrow().status())
                .isEqualTo(MemoryStatus.INVALIDATED);
    }

    @Test
    void confirmedQueryScopesOwnerAndCompanionBeforeApplyingLimit() {
        CompanionMemory older = memoryRepository.append(CompanionMemory.candidate(604L, 7L, 101L, 701L,
                MemorySourceType.VISUAL, "较早确认。", bd("0.8"), NOW).confirm(NOW));
        CompanionMemory newer = memoryRepository.append(CompanionMemory.candidate(604L, 7L, 101L, 702L,
                MemorySourceType.VISUAL, "较新确认。", bd("0.8"), NOW.plusSeconds(1)).confirm(NOW.plusSeconds(1)));
        memoryRepository.append(CompanionMemory.candidate(604L, 999L, 101L, 703L,
                MemorySourceType.VISUAL, "其他主体。", bd("0.8"), NOW.plusSeconds(2)).confirm(NOW.plusSeconds(2)));
        memoryRepository.append(CompanionMemory.candidate(605L, 7L, 101L, 704L,
                MemorySourceType.VISUAL, "其他伙伴。", bd("0.8"), NOW.plusSeconds(3)).confirm(NOW.plusSeconds(3)));

        assertThat(memoryRepository.findConfirmed(604L, 7L, 1))
                .extracting(CompanionMemory::id).containsExactly(newer.id());
        assertThat(memoryRepository.findConfirmed(604L, 7L, 100))
                .extracting(CompanionMemory::id).containsExactly(newer.id(), older.id());
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
