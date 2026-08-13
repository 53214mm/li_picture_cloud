package com.li.lipicturecloud.domain.companion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * 一条主动提案：由机会源产生、经契约守门后进入 PENDING。
 *
 * <p>内容由系统确定性生成，安全纯文本（无控制字符与外部链接）；状态机
 * PENDING → DONE/IGNORED/SUPPRESSED/EXPIRED，终态不可再变更。</p>
 */
public record CompanionProposal(
        Long id,
        long companionId,
        long subjectId,
        ProposalOpportunityType opportunityType,
        BigDecimal impulseScore,
        String content,
        ProposalStatus status,
        String gateResult,
        long revision,
        Instant createdTime,
        Instant updatedTime) {

    public static final int MAX_CONTENT_CODE_POINTS = 500;

    public CompanionProposal {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (companionId <= 0 || subjectId <= 0 || revision < 0) {
            throw new IllegalArgumentException("invalid proposal identity or revision");
        }
        Objects.requireNonNull(opportunityType, "opportunityType");
        Objects.requireNonNull(impulseScore, "impulseScore");
        impulseScore = impulseScore.setScale(2, java.math.RoundingMode.HALF_UP);
        if (impulseScore.compareTo(BigDecimal.ZERO) < 0 || impulseScore.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException("impulseScore must be between 0 and 100");
        }
        content = checkContent(content);
        Objects.requireNonNull(status, "status");
        if (status == ProposalStatus.SUPPRESSED) {
            gateResult = checkGateResult(gateResult);
        } else {
            if (gateResult != null) {
                throw new IllegalArgumentException("only SUPPRESSED proposal may carry gateResult");
            }
        }
        Objects.requireNonNull(createdTime, "createdTime");
        Objects.requireNonNull(updatedTime, "updatedTime");
        if (updatedTime.isBefore(createdTime)) {
            throw new IllegalArgumentException("updatedTime cannot be before createdTime");
        }
    }

    public static CompanionProposal pending(long companionId, long subjectId,
                                            ProposalOpportunityType opportunityType,
                                            BigDecimal impulseScore, String content, Instant now) {
        return new CompanionProposal(null, companionId, subjectId, opportunityType, impulseScore,
                content, ProposalStatus.PENDING, null, 0L, now, now);
    }

    public static CompanionProposal restore(Long id, long companionId, long subjectId,
                                            ProposalOpportunityType opportunityType,
                                            BigDecimal impulseScore, String content,
                                            ProposalStatus status, String gateResult,
                                            long revision, Instant createdTime, Instant updatedTime) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("persisted id must be positive");
        }
        return new CompanionProposal(id, companionId, subjectId, opportunityType, impulseScore,
                content, status, gateResult, revision, createdTime, updatedTime);
    }

    public CompanionProposal withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new CompanionProposal(persistedId, companionId, subjectId, opportunityType, impulseScore,
                content, status, gateResult, revision, createdTime, updatedTime);
    }

    public CompanionProposal accept(Instant now) {
        requirePending("accept");
        return transition(ProposalStatus.DONE, null, now);
    }

    public CompanionProposal ignore(Instant now) {
        requirePending("ignore");
        return transition(ProposalStatus.IGNORED, null, now);
    }

    public CompanionProposal scold(Instant now) {
        requirePending("scold");
        return transition(ProposalStatus.SUPPRESSED, "SCOLDED", now);
    }

    public CompanionProposal expire(Instant now) {
        requirePending("expire");
        return transition(ProposalStatus.EXPIRED, null, now);
    }

    private void requirePending(String action) {
        if (status != ProposalStatus.PENDING) {
            throw new IllegalStateException("cannot " + action + " a non-pending proposal");
        }
    }

    private CompanionProposal transition(ProposalStatus next, String reason, Instant now) {
        return new CompanionProposal(id, companionId, subjectId, opportunityType, impulseScore,
                content, next, reason, Math.addExact(revision, 1L), createdTime,
                Objects.requireNonNull(now, "now"));
    }

    private static String checkGateResult(String reason) {
        String normalized = Objects.requireNonNull(reason, "gateResult").strip();
        if (normalized.isEmpty() || normalized.length() > 64) {
            throw new IllegalArgumentException("gateResult must be 1-64 characters");
        }
        return normalized;
    }

    private static String checkContent(String value) {
        String normalized = Objects.requireNonNull(value, "content").strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > MAX_CONTENT_CODE_POINTS) {
            throw new IllegalArgumentException("proposal content must be 1-500 characters");
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)
                || containsExternalLink(normalized)) {
            throw new IllegalArgumentException("proposal content must be safe plain text");
        }
        return normalized;
    }

    private static boolean containsExternalLink(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("http://") || lower.contains("https://") || lower.contains("www.");
    }
}
