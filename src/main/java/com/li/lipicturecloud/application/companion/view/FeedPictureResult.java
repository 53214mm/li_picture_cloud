package com.li.lipicturecloud.application.companion.view;

/**
 * 一次喂养的稳定返回契约，包含最新伙伴快照和本次成长事实。
 *
 * <p>{@code correlationId} 用于跨日志追踪；幂等回放会返回原来的结果而不是再次成长。</p>
 */
public record FeedPictureResult(String outcome, String correlationId,
                                CompanionView companion, GrowthRecordView growth) {
}
