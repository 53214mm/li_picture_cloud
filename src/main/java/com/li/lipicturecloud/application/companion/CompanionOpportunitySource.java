package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.ProposalOpportunityType;

import java.time.Instant;
import java.util.Optional;

/**
 * 主动提案的两段式机会源：先轻量观察（Observe，守门之前），守门通过后
 * 才物化为完整候选（Materialize：冲动评分 + 文案）。
 *
 * <p>观察阶段只做最廉价的候选存在性判断并返回机会类型，不读取情绪、不写回、
 * 不调用授权、不做空间统计、不生成文案；这样硬门禁（契约关闭/安静时段/频率）
 * 失败时系统不会执行任何候选级工作，只记录 type + reason。文案必须确定性生成；
 * 任何阶段都不产生外部调用。</p>
 */
public interface CompanionOpportunitySource {

    /** 本机会源的机会类型：用于把"三类机会的生成与拦截"写入可观测日志。 */
    ProposalOpportunityType type();

    /**
     * 守门前的轻量观察：判断是否存在该类型机会的最小事实（只查计数/最近列表）。
     * 契约关闭、安静时段等场景下仍会被调用，用于产出可区分类型的拦截日志。
     */
    Optional<OpportunityObservation> observe(long companionId, long subjectId, Instant now);

    /**
     * 守门通过后的完整物化：复验候选条件，然后读取衰减后的当前情绪并冲动评分、
     * 校验权限/统计（视类型需要）、生成确定性文案并构造候选。
     */
    Optional<ProposalOpportunity> materialize(OpportunityObservation observation,
                                              long companionId, long subjectId, Instant now);
}
