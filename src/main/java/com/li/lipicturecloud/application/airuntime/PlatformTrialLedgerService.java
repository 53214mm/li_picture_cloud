package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.config.ModelCredentialProperties;
import com.li.lipicturecloud.domain.airuntime.InsufficientTrialBalanceException;
import com.li.lipicturecloud.domain.airuntime.PlatformTrialLedger;
import com.li.lipicturecloud.domain.airuntime.PlatformTrialLedgerRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.function.UnaryOperator;

/**
 * 平台试用额度账本服务：报价外的预占/结算/释放三原语，全部走 revision CAS 重试，
 * 保证并发下余额与预占永不为负。超限停止不自动扣费。
 */
@Service
public class PlatformTrialLedgerService {

    private static final int MAX_CAS_RETRIES = 5;

    private final PlatformTrialLedgerRepository ledgerRepository;
    private final ModelCredentialProperties properties;

    public PlatformTrialLedgerService(PlatformTrialLedgerRepository ledgerRepository,
                                      ModelCredentialProperties properties) {
        this.ledgerRepository = ledgerRepository;
        this.properties = properties;
    }

    public PlatformTrialLedger getOrCreate(long subjectId) {
        checkIdentity(subjectId);
        return ledgerRepository.findBySubjectId(subjectId)
                .orElseGet(() -> ledgerRepository.insert(PlatformTrialLedger.create(
                        subjectId, properties.getTrialDefaultBalance())));
    }

    public long available(long subjectId) {
        return getOrCreate(subjectId).available();
    }

    public PlatformTrialLedger reserve(long subjectId, long amount) {
        checkAmount(amount);
        try {
            return updateWithRetry(subjectId, ledger -> ledger.reserve(amount));
        } catch (InsufficientTrialBalanceException insufficient) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "平台试用额度不足：可用 " + insufficient.available()
                            + "，需要 " + insufficient.requested());
        }
    }

    public PlatformTrialLedger settle(long subjectId, long amount) {
        checkAmount(amount);
        return updateWithRetry(subjectId, ledger -> ledger.settle(amount));
    }

    public PlatformTrialLedger release(long subjectId, long amount) {
        checkAmount(amount);
        return updateWithRetry(subjectId, ledger -> ledger.release(amount));
    }

    public PlatformTrialLedger grant(long subjectId, long amount) {
        checkIdentity(subjectId);
        checkAmount(amount);
        return updateWithRetry(subjectId, ledger -> ledger.grant(amount));
    }

    private PlatformTrialLedger updateWithRetry(long subjectId,
                                                UnaryOperator<PlatformTrialLedger> operation) {
        for (int attempt = 0; attempt < MAX_CAS_RETRIES; attempt++) {
            PlatformTrialLedger current = getOrCreate(subjectId);
            PlatformTrialLedger after = operation.apply(current);
            if (ledgerRepository.save(after, current.revision())) {
                return after;
            }
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "试用账本并发冲突，请重试");
    }

    private static void checkIdentity(long subjectId) {
        if (subjectId <= 0) {
            throw new IllegalArgumentException("subjectId must be positive");
        }
    }

    private static void checkAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
