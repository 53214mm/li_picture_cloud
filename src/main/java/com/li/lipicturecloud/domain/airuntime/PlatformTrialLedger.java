package com.li.lipicturecloud.domain.airuntime;

/**
 * 平台试用额度账本（每主体一行）。不变量：balance ≥ 0、reserved ≥ 0、
 * balance ≥ reserved（可用额度永不为负）。平台试用永远硬上限，超限停止不自动扣费。
 */
public record PlatformTrialLedger(
        Long id,
        long subjectId,
        long balance,
        long reserved,
        long revision) {

    public PlatformTrialLedger {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (subjectId <= 0 || balance < 0 || reserved < 0 || revision < 0) {
            throw new IllegalArgumentException("invalid trial ledger identity or amounts");
        }
        if (balance < reserved) {
            throw new IllegalArgumentException("available trial balance must never be negative");
        }
    }

    public static PlatformTrialLedger create(long subjectId, long initialBalance) {
        if (subjectId <= 0 || initialBalance < 0) {
            throw new IllegalArgumentException("invalid trial ledger creation arguments");
        }
        return new PlatformTrialLedger(null, subjectId, initialBalance, 0L, 0L);
    }

    public static PlatformTrialLedger restore(Long id, long subjectId, long balance,
                                              long reserved, long revision) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("persisted id must be positive");
        }
        return new PlatformTrialLedger(id, subjectId, balance, reserved, revision);
    }

    public PlatformTrialLedger withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new PlatformTrialLedger(persistedId, subjectId, balance, reserved, revision);
    }

    public long available() {
        return balance - reserved;
    }

    /** 预占：可用额度不足时抛出；成功则 reserved += amount。 */
    public PlatformTrialLedger reserve(long amount) {
        requirePositiveAmount(amount);
        if (available() < amount) {
            throw new InsufficientTrialBalanceException(available(), amount);
        }
        return advance(balance, Math.addExact(reserved, amount));
    }

    /** 结算：预占必须足额；balance 与 reserved 同减。 */
    public PlatformTrialLedger settle(long amount) {
        requirePositiveAmount(amount);
        if (reserved < amount) {
            throw new IllegalArgumentException("settle exceeds reserved trial balance");
        }
        return advance(Math.subtractExact(balance, amount), Math.subtractExact(reserved, amount));
    }

    /** 释放：失败/取消时退回预占，不动余额。 */
    public PlatformTrialLedger release(long amount) {
        requirePositiveAmount(amount);
        if (reserved < amount) {
            throw new IllegalArgumentException("release exceeds reserved trial balance");
        }
        return advance(balance, Math.subtractExact(reserved, amount));
    }

    /** 平台授予（管理面）。 */
    public PlatformTrialLedger grant(long amount) {
        requirePositiveAmount(amount);
        return advance(Math.addExact(balance, amount), reserved);
    }

    private PlatformTrialLedger advance(long nextBalance, long nextReserved) {
        return new PlatformTrialLedger(id, subjectId, nextBalance, nextReserved,
                Math.addExact(revision, 1L));
    }

    private static void requirePositiveAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
