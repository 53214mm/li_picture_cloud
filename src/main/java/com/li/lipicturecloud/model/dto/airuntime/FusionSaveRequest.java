package com.li.lipicturecloud.model.dto.airuntime;

import lombok.Data;

/**
 * 融合作品保存请求：目标空间 + 作品名。空间必须显式选择（规格 §7）。
 */
@Data
public class FusionSaveRequest {
    private Long spaceId;
    private String name;
}
