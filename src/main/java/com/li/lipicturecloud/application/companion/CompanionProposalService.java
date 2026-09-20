package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.companion.view.CompanionContractView;
import com.li.lipicturecloud.application.companion.view.CompanionProposalView;
import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionAutonomyContract;
import com.li.lipicturecloud.domain.companion.CompanionAutonomyContractRepository;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.domain.companion.CompanionProposal;
import com.li.lipicturecloud.domain.companion.CompanionProposalReaction;
import com.li.lipicturecloud.domain.companion.CompanionProposalReactionRepository;
import com.li.lipicturecloud.domain.companion.CompanionProposalRepository;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
import com.li.lipicturecloud.domain.companion.ProposalGate;
import com.li.lipicturecloud.domain.companion.ProposalOpportunityType;
import com.li.lipicturecloud.domain.companion.ProposalReactionType;
import com.li.lipicturecloud.domain.companion.TraitDelta;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 自主契约与主动提案：契约查询/更新，机会感知、冲动决策、守门、提案落库，用户反馈。
 *
 * <p>提案在读取活跃提案时惰性生成与惰性过期；守门与冲动拦截只记录指标，不打扰用户。
 * 冲动得分由机会评估器按"当前情绪 + 关系"计算，只影响"是否生成候选"：低于最低冲动
 * （零积累）的机会不落库并记 reason=IMPULSE_ZERO。重复敲打（30 天内满 3 次）才缓慢
 * 下调"好奇"性格，单次敲打只抑制当前提案。</p>
 */
@Service
@ConditionalOnProperty(prefix = "app.companion", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CompanionProposalService {

    private static final Logger log = LoggerFactory.getLogger(CompanionProposalService.class);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Duration PROPOSAL_TTL = Duration.ofHours(48);
    private static final Duration SCOLD_WINDOW = Duration.ofDays(30);
    private static final long SCOLD_TRAIT_THRESHOLD = 3L;

    private final CompanionRepository companionRepository;
    private final CompanionAutonomyContractRepository contractRepository;
    private final CompanionProposalRepository proposalRepository;
    private final CompanionProposalReactionRepository reactionRepository;
    private final List<CompanionOpportunitySource> opportunitySources;
    private final List<CompanionOpportunityListener> opportunityListeners;
    private final ProposalOpportunityEvaluator evaluator;
    private final CompanionBalance balance;
    private final Clock clock;

    public CompanionProposalService(CompanionRepository companionRepository,
                                    CompanionAutonomyContractRepository contractRepository,
                                    CompanionProposalRepository proposalRepository,
                                    CompanionProposalReactionRepository reactionRepository,
                                    List<CompanionOpportunitySource> opportunitySources,
                                    List<CompanionOpportunityListener> opportunityListeners,
                                    ProposalOpportunityEvaluator evaluator,
                                    CompanionBalance balance,
                                    Clock clock) {
        this.companionRepository = companionRepository;
        this.contractRepository = contractRepository;
        this.proposalRepository = proposalRepository;
        this.reactionRepository = reactionRepository;
        // 机会源按 @Order 优先级短路尝试（每周回顾 → 纪念日 → 相似图片），
        // 第一个"有候选且冲动得分达标"的产生提案；低于阈值的候选记拦截后继续下一个源。
        this.opportunitySources = List.copyOf(opportunitySources);
        // 机会监听者（阶段 5 配方 WHEN 等）：守门通过后复用同一机会，不产生任何自动执行。
        this.opportunityListeners = List.copyOf(opportunityListeners);
        this.evaluator = evaluator;
        this.balance = balance;
        this.clock = clock;
    }

    @Transactional
    public CompanionContractView contract(AuthorizationSubject subject) {
        Companion companion = requireCompanion(subject);
        CompanionAutonomyContract contract = contractRepository
                .createIfAbsent(companion.id(), subject.userId());
        return view(contract);
    }

    @Transactional
    public CompanionContractView updateContract(AuthorizationSubject subject, boolean nextActive,
                                                LocalTime nextQuietStart, LocalTime nextQuietEnd,
                                                int nextMaxFrequencyHours) {
        Companion companion = requireCompanion(subject);
        CompanionAutonomyContract contract = contractRepository
                .createIfAbsent(companion.id(), subject.userId());
        CompanionAutonomyContract after = contract.updated(nextActive, nextQuietStart, nextQuietEnd,
                nextMaxFrequencyHours);
        if (!contractRepository.save(after, contract.revision())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "契约状态已变化，请重试");
        }
        log.info("companion_contract_updated subjectId={} active={} frequencyHours={}",
                subject.userId(), after.active(), after.maxFrequencyHours());
        return view(after);
    }

    @Transactional
    public CompanionProposalView active(AuthorizationSubject subject) {
        // 伙伴行锁串行化提案生成：并发访问不会产生两条 PENDING。
        Companion companion = companionRepository.findByOwnerIdForUpdate(subject.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "请先唤醒伙伴"));
        CompanionProposal proposal = maybePropose(companion, subject, clock.instant());
        if (proposal == null) {
            return null;
        }
        log.info("companion_proposal_viewed subjectId={} proposalId={} type={}",
                subject.userId(), proposal.id(), proposal.opportunityType().name());
        return view(proposal);
    }

    @Transactional
    public CompanionProposalView accept(AuthorizationSubject subject, long proposalId) {
        return react(subject, proposalId, ProposalReactionType.ACCEPT);
    }

    @Transactional
    public CompanionProposalView ignore(AuthorizationSubject subject, long proposalId) {
        return react(subject, proposalId, ProposalReactionType.IGNORE);
    }

    @Transactional
    public CompanionProposalView scold(AuthorizationSubject subject, long proposalId) {
        CompanionProposalView view = react(subject, proposalId, ProposalReactionType.SCOLD);
        Instant now = clock.instant();
        long scolds = reactionRepository.countScoldsSince(subject.userId(), now.minus(SCOLD_WINDOW));
        if (scolds >= SCOLD_TRAIT_THRESHOLD && scolds % SCOLD_TRAIT_THRESHOLD == 0) {
            applyCuriosityPenalty(subject);
        }
        return view;
    }

    private CompanionProposalView react(AuthorizationSubject subject, long proposalId,
                                        ProposalReactionType reactionType) {
        Companion companion = requireCompanion(subject);
        CompanionProposal proposal = proposalRepository.findById(proposalId)
                .filter(value -> value.companionId() == companion.id() && value.subjectId() == subject.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "提案不存在"));
        Instant now = clock.instant();
        CompanionProposal after;
        try {
            after = switch (reactionType) {
                case ACCEPT -> proposal.accept(now);
                case IGNORE -> proposal.ignore(now);
                case SCOLD -> proposal.scold(now);
            };
        } catch (IllegalStateException invalid) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "提案已处理，不能重复操作");
        }
        if (!proposalRepository.save(after, proposal.revision())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "提案状态已变化，请重试");
        }
        reactionRepository.append(CompanionProposalReaction.of(proposal.id(), subject.userId(),
                reactionType, now));
        log.info("companion_proposal_reaction subjectId={} proposalId={} type={}",
                subject.userId(), proposal.id(), reactionType.name());
        return view(after);
    }

    private CompanionProposal maybePropose(Companion companion, AuthorizationSubject subject, Instant now) {
        // 惰性过期：超时未响应的 PENDING 提案转为 EXPIRED。
        for (CompanionProposal pending : proposalRepository.findActive(companion.id(), 5)) {
            if (now.isAfter(pending.createdTime().plus(PROPOSAL_TTL))) {
                CompanionProposal expired = pending.expire(now);
                if (!proposalRepository.save(expired, pending.revision())) {
                    log.warn("companion_proposal_expire_conflict proposalId={}", pending.id());
                }
            }
        }
        List<CompanionProposal> stillActive = proposalRepository.findActive(companion.id(), 5);
        if (!stillActive.isEmpty()) {
            return stillActive.get(0);
        }
        CompanionAutonomyContract contract = contractRepository
                .createIfAbsent(companion.id(), subject.userId());
        CompanionProposal latest = proposalRepository.findRecent(companion.id(), 1).stream()
                .findFirst().orElse(null);
        ProposalGate.GateResult gate = ProposalGate.check(contract, now, SHANGHAI,
                latest == null ? null : latest.createdTime());
        // 三段式顺序（Observe → 守门 → Materialize）：
        // 1) 机会源先轻量观察：只返回真实机会类型与最小事实（不评分、不读取/写回情绪、
        //    不校验授权、不统计、不生成文案），使硬门禁拦截日志能带上类型；
        // 2) 有观察后执行守门：失败只记 companion_proposal_gated（type + reason）并停止，
        //    此时不会发生任何候选级工作，满足"守门先于候选生成"的硬门禁边界；
        // 3) 守门通过后才物化完整候选（冲动评分、文案等），再经冲动阈值后落库。
        // 没有任何候选时只记 NO_CANDIDATE，不计为 gated，避免把轮询当拦截率分子。
        for (CompanionOpportunitySource source : opportunitySources) {
            Optional<OpportunityObservation> observed = source.observe(
                    companion.id(), subject.userId(), now);
            if (observed.isEmpty()) {
                log.info("companion_proposal_opportunity subjectId={} type={} result=NO_CANDIDATE",
                        subject.userId(), sourceTypeName(source));
                continue;
            }
            OpportunityObservation observation = observed.get();
            if (!gate.passed()) {
                log.info("companion_proposal_gated subjectId={} proposalId=none type={} reason={}",
                        subject.userId(), observation.type().name(), gate.reasonCode());
                return null;
            }
            // 守门通过且机会真实存在：把同一机会交给机会监听者（阶段 5 配方 WHEN），
            // 只产生"待用户确认"的结果，绝不在这里触发任何模型调用或自动执行。
            notifyOpportunity(subject.userId(), companion.id(), observation, now);
            Optional<ProposalOpportunity> materialized = source.materialize(
                    observation, companion.id(), subject.userId(), now);
            if (materialized.isEmpty()) {
                log.info("companion_proposal_opportunity subjectId={} type={} result=NO_CANDIDATE",
                        subject.userId(), observation.type().name());
                continue;
            }
            ProposalOpportunity opportunity = materialized.get();
            if (!evaluator.reachesProposalThreshold(opportunity.impulseScore())) {
                log.info("companion_proposal_opportunity subjectId={} type={} result=BELOW_THRESHOLD "
                                + "reason=IMPULSE_ZERO score={}",
                        subject.userId(), opportunity.type().name(), opportunity.impulseScore());
                continue;
            }
            CompanionProposal saved = proposalRepository.append(CompanionProposal.pending(
                    companion.id(), subject.userId(), opportunity.type(),
                    opportunity.impulseScore(), opportunity.content(), now));
            log.info("companion_proposal_generated subjectId={} proposalId={} type={}",
                    subject.userId(), saved.id(), saved.opportunityType().name());
            return saved;
        }
        return null;
    }

    private static String sourceTypeName(CompanionOpportunitySource source) {
        ProposalOpportunityType type = source.type();
        return type == null ? "UNKNOWN" : type.name();
    }

    /**
     * 通知机会监听者。监听者失败只告警：配方 WHEN 触发出问题绝不能影响伙伴提案主链路
     * （机会本身仍会在下一次观察时被重新投递）。上下文传递强类型机会与本次真实目标图片，
     * 避免下游拿机会锚点（旧喂养图）当目标图片。
     */
    private void notifyOpportunity(long subjectId, long companionId,
                                   OpportunityObservation observation, Instant now) {
        OpportunityContext context = OpportunityContext.from(observation);
        for (CompanionOpportunityListener listener : opportunityListeners) {
            try {
                listener.onOpportunity(subjectId, companionId, context, now);
            } catch (RuntimeException listenerFailure) {
                log.warn("companion_opportunity_listener_failed subjectId={} type={} listener={}",
                        subjectId, observation.type().name(),
                        listener.getClass().getSimpleName());
            }
        }
    }

    private void applyCuriosityPenalty(AuthorizationSubject subject) {
        Companion locked = companionRepository.findByOwnerIdForUpdate(subject.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "请先唤醒伙伴"));
        TraitDelta penalty = new TraitDelta(
                balance.applyTrait(locked.traits().curiosity(), new BigDecimal("-0.50")),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        if (penalty.curiosity().signum() == 0) {
            return;
        }
        Companion after = locked.applyFeedback(penalty, balance);
        if (companionRepository.save(after, locked.revision())) {
            log.info("companion_scold_trait_applied subjectId={} curiosityDelta={}",
                    subject.userId(), penalty.curiosity());
            return;
        }
        // 与并发喂养 CAS 竞争失败时重读并重试一次；仍失败才静默丢弃（下次敲打会再评估）。
        Companion current = companionRepository.findByOwnerIdForUpdate(subject.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "请先唤醒伙伴"));
        TraitDelta retriedPenalty = new TraitDelta(
                balance.applyTrait(current.traits().curiosity(), new BigDecimal("-0.50")),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        if (retriedPenalty.curiosity().signum() == 0) {
            return;
        }
        Companion retried = current.applyFeedback(retriedPenalty, balance);
        if (companionRepository.save(retried, current.revision())) {
            log.info("companion_scold_trait_applied subjectId={} curiosityDelta={} retried=true",
                    subject.userId(), retriedPenalty.curiosity());
        } else {
            log.warn("companion_scold_trait_conflict subjectId={}", subject.userId());
        }
    }

    private Companion requireCompanion(AuthorizationSubject subject) {
        return companionRepository.findByOwnerId(subject.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "请先唤醒伙伴"));
    }

    private CompanionContractView view(CompanionAutonomyContract contract) {
        return new CompanionContractView(contract.active(), contract.quietStart(), contract.quietEnd(),
                contract.maxFrequencyHours(), contract.revision());
    }

    private CompanionProposalView view(CompanionProposal proposal) {
        return new CompanionProposalView(proposal.id(), proposal.opportunityType().name(),
                proposal.impulseScore(), proposal.content(), proposal.status().name(),
                proposal.gateResult(), proposal.createdTime());
    }
}
