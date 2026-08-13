package com.li.lipicturecloud.model.dto.companion;

import lombok.Data;

@Data
public class CompanionContractUpdateRequest {
    private Boolean active;
    private String quietStart;
    private String quietEnd;
    private Integer maxFrequencyHours;
}
