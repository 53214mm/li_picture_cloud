package com.li.lipicturecloud.domain.companion;

/**
 * 记忆候选的观察来源。
 *
 * <p>只有真实视觉理解（或 Demo 测试档）产生记忆候选；元数据喂养与显式降级
 * 没有内容理解，不生成候选。</p>
 */
public enum MemorySourceType {
    VISUAL,
    DEMO
}
