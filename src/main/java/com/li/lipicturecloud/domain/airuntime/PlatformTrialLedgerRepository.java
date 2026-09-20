package com.li.lipicturecloud.domain.airuntime;

import java.util.Optional;

/**
 * 平台试用额度账本的持久化端口。每主体一行（subjectId 唯一），写入走 revision CAS。
 */
public interface PlatformTrialLedgerRepository {

    Optional<PlatformTrialLedger> findBySubjectId(long subjectId);

    PlatformTrialLedger insert(PlatformTrialLedger ledger);

    /** after.revision 必须恰好等于 expectedRevision + 1。 */
    boolean save(PlatformTrialLedger after, long expectedRevision);
}
