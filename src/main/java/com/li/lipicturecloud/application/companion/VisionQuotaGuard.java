package com.li.lipicturecloud.application.companion;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 平台钱包的视觉调用闸门。
 *
 * <p>额度在图片字节离开本服务之前预占。调用失败或随后降级都不归还次数，避免可重试的
 * 网络错误被用来无限消耗平台 Token。</p>
 */
public interface VisionQuotaGuard {

    /**
     * 为一个主体在一个上海自然日内预占一次视觉调用额度。
     *
     * @throws com.li.lipicturecloud.exception.BusinessException 当日额度已经耗尽
     */
    VisionQuotaReservation reserve(long subjectId, LocalDate usageDate, int dailyLimit);

    /**
     * 已提交的日桶快照；{@code used} 是本次预占完成后的值。
     */
    record VisionQuotaReservation(LocalDate usageDate, int used, int limit) {
        public VisionQuotaReservation {
            Objects.requireNonNull(usageDate, "usageDate");
            if (limit <= 0 || used < 1 || used > limit) {
                throw new IllegalArgumentException("vision quota reservation is outside its daily limit");
            }
        }
    }
}
