package com.li.lipicturecloud.application.companion;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 伙伴对话的每日轮次闸门。
 *
 * <p>额度在用户消息外发模型之前预占；失败与中断不退还次数，防止可重试错误无限消耗平台成本。</p>
 */
public interface ChatQuotaGuard {

    /**
     * 为一个主体在一个上海自然日内预占一次对话轮次。
     *
     * @throws com.li.lipicturecloud.exception.BusinessException 当日额度已经耗尽
     */
    ChatQuotaReservation reserve(long subjectId, LocalDate usageDate, int dailyLimit);

    /**
     * 已提交的日桶快照；{@code used} 是本次预占完成后的值。
     */
    record ChatQuotaReservation(LocalDate usageDate, int used, int limit) {
        public ChatQuotaReservation {
            Objects.requireNonNull(usageDate, "usageDate");
            if (limit <= 0 || used < 1 || used > limit) {
                throw new IllegalArgumentException("chat quota reservation is outside its daily limit");
            }
        }
    }
}
