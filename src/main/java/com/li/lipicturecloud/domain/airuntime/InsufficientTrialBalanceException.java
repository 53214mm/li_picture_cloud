package com.li.lipicturecloud.domain.airuntime;

/**
 * 平台试用额度不足：超限停止，绝不自动扣费。
 */
public class InsufficientTrialBalanceException extends RuntimeException {

    private final long available;
    private final long requested;

    public InsufficientTrialBalanceException(long available, long requested) {
        super("试用额度不足：可用 " + available + "，需要 " + requested);
        this.available = available;
        this.requested = requested;
    }

    public long available() {
        return available;
    }

    public long requested() {
        return requested;
    }
}
