package com.li.lipicturecloud.model.dto.companion;

import lombok.Data;

@Data
public class CompanionFeedRequest {
    private Long pictureId;
    private String idempotencyKey;
}
