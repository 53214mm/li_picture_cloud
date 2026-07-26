package com.li.lipicturecloud.model.dto.space.analyze;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class SpaceUserAnalyzeRequest extends SpaceAnalyzeRequest {

    /** 用户 ID（可选，筛选特定用户的上传数据） */
    private Long userId;

    /**
     * 时间维度：{@code day} / {@code week} / {@code month}
     * <p>
     * day — 按日聚合（period: YYYY-MM-DD）
     * week — 按周聚合（period: MySQL YEARWEEK 值）
     * month — 按月聚合（period: YYYY-MM）
     */
    private String timeDimension;
}
