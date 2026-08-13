package com.li.lipicturecloud.application.companion;

/**
 * 可安全展示和审计的视觉阶段失败分类。
 *
 * <p>代码是未来降级策略的唯一输入；异常消息与 cause 都不能承载图片地址、字节或模型原文。</p>
 */
public interface VisionSafeFailure {
    String safeCode();
}
