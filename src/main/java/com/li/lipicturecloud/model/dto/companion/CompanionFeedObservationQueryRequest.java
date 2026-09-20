package com.li.lipicturecloud.model.dto.companion;

import com.li.lipicturecloud.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.Instant;

/** 管理员喂养观测分页查询条件。 */
@EqualsAndHashCode(callSuper = true)
@Data
public class CompanionFeedObservationQueryRequest extends PageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long subjectId;
    private Long pictureId;
    private String userKeyword;
    private String status;
    private String requestedPolicy;
    private String safeErrorCode;
    private String correlationId;
    private Instant startTime;
    private Instant endTime;
}
