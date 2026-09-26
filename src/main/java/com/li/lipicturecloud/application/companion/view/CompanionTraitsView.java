package com.li.lipicturecloud.application.companion.view;

import java.math.BigDecimal;

/** 前端展示的五条性格轴；只负责传输数值，不负责裁剪或计算属性。 */
public record CompanionTraitsView(BigDecimal curiosity, BigDecimal enthusiasm, BigDecimal playfulness,
                                  BigDecimal empathy, BigDecimal creativity) {
}
