package com.li.lipicturecloud.application.airuntime.view;

import com.li.lipicturecloud.domain.airuntime.PlatformTrialLedger;

/**
 * 平台试用额度展示视图：可用额度 = balance - reserved，永不为负。
 */
public record TrialBalanceView(
        long subjectId,
        long balance,
        long reserved,
        long available) {

    public static TrialBalanceView of(PlatformTrialLedger ledger) {
        return new TrialBalanceView(ledger.subjectId(), ledger.balance(), ledger.reserved(),
                ledger.available());
    }
}
