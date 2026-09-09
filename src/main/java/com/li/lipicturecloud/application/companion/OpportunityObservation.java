package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.ProposalOpportunityType;

import java.util.Objects;

/**
 * 机会观察：机会源在守门之前产生的"最轻量候选事实"——只含真实机会类型，
 * 不携带冲动评分、文案，也不触发情绪读取/写回、图片权限校验或空间统计。
 *
 * <p>观察结果用于：守门失败时记录"被拦截的是哪类真实机会"（type + reason），
 * 同时保证契约关闭/安静时段等硬门禁失败时不会发生任何候选级工作
 * （评分、情绪衰减写回、授权、统计、文案），满足"守门先于候选生成"的边界。</p>
 */
public record OpportunityObservation(ProposalOpportunityType type) {

    public OpportunityObservation {
        Objects.requireNonNull(type, "type");
    }
}
