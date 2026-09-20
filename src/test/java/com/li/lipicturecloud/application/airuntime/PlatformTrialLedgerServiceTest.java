package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.config.ModelCredentialProperties;
import com.li.lipicturecloud.domain.airuntime.PlatformTrialLedger;
import com.li.lipicturecloud.domain.airuntime.PlatformTrialLedgerRepository;
import com.li.lipicturecloud.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformTrialLedgerServiceTest {

    private PlatformTrialLedgerRepository ledgerRepository;
    private ModelCredentialProperties properties;
    private PlatformTrialLedgerService service;

    @BeforeEach
    void setUp() {
        ledgerRepository = mock(PlatformTrialLedgerRepository.class);
        properties = new ModelCredentialProperties();
        properties.setTrialDefaultBalance(100L);
        service = new PlatformTrialLedgerService(ledgerRepository, properties);
    }

    private PlatformTrialLedger ledger(long balance, long reserved, long revision) {
        return PlatformTrialLedger.restore(3L, 7L, balance, reserved, revision);
    }

    @Test
    void getOrCreateLazilySeedsTheConfiguredTrialBalance() {
        when(ledgerRepository.findBySubjectId(7L)).thenReturn(Optional.empty());
        when(ledgerRepository.insert(any(PlatformTrialLedger.class))).thenAnswer(invocation ->
                invocation.<PlatformTrialLedger>getArgument(0).withId(3L));

        assertThat(service.available(7L)).isEqualTo(100L);
    }

    @Test
    void reserveSettlesAndReleasesThroughCas() {
        when(ledgerRepository.findBySubjectId(7L)).thenReturn(Optional.of(ledger(100L, 0L, 4L)));
        when(ledgerRepository.save(any(PlatformTrialLedger.class), eq(4L))).thenReturn(true);

        assertThat(service.reserve(7L, 10L).reserved()).isEqualTo(10L);
        verify(ledgerRepository).save(any(PlatformTrialLedger.class), eq(4L));
    }

    @Test
    void insufficientBalanceSurfacesAsBusinessError() {
        when(ledgerRepository.findBySubjectId(7L)).thenReturn(Optional.of(ledger(3L, 0L, 4L)));

        assertThatThrownBy(() -> service.reserve(7L, 5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("试用额度不足");
    }

    @Test
    void retriesOnCasConflictThenGivesUpLoudly() {
        when(ledgerRepository.findBySubjectId(7L)).thenReturn(Optional.of(ledger(100L, 0L, 4L)));
        when(ledgerRepository.save(any(PlatformTrialLedger.class), anyLong())).thenReturn(false);

        assertThatThrownBy(() -> service.reserve(7L, 5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("并发冲突");
        verify(ledgerRepository, times(5)).save(any(PlatformTrialLedger.class), anyLong());
    }

    @Test
    void grantTopsUpAndRejectsBadArguments() {
        when(ledgerRepository.findBySubjectId(7L)).thenReturn(Optional.of(ledger(0L, 0L, 4L)));
        when(ledgerRepository.save(any(PlatformTrialLedger.class), eq(4L))).thenReturn(true);

        assertThat(service.grant(7L, 50L).balance()).isEqualTo(50L);
        assertThatThrownBy(() -> service.grant(0L, 10L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.reserve(7L, 0L)).isInstanceOf(IllegalArgumentException.class);
    }
}
