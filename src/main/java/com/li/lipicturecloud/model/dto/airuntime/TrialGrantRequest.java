package com.li.lipicturecloud.model.dto.airuntime;

import lombok.Data;

/**
 * 平台试用额度授予请求（仅管理员）。
 */
@Data
public class TrialGrantRequest {
    private Long subjectId;
    private Long amount;
}
