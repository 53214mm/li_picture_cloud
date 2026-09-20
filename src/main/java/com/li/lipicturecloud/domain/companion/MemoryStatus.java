package com.li.lipicturecloud.domain.companion;

/**
 * 记忆的生命周期状态。
 *
 * <p>{@code INVALIDATED} 与 {@code DELETED} 是终态；前者因来源图片撤权或消失产生，
 * 后者由用户主动删除。终态记忆不再对外暴露内容原文。</p>
 */
public enum MemoryStatus {
    PENDING,
    CONFIRMED,
    DISMISSED,
    INVALIDATED,
    DELETED
}
