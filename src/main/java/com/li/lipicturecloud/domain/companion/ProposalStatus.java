package com.li.lipicturecloud.domain.companion;

/**
 * 提案生命周期。DONE/IGNORED/SUPPRESSED/EXPIRED 为终态。
 */
public enum ProposalStatus {
    PENDING,
    DONE,
    IGNORED,
    SUPPRESSED,
    EXPIRED
}
