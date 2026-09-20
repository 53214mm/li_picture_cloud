package com.li.lipicturecloud.model.dto.airuntime;

import lombok.Data;

import java.util.List;

/**
 * 图片故事创作请求：授权图片 + 幂等键（重试沿用同一键不会重复创建）。
 */
@Data
public class CreationCreateRequest {
    private List<Long> pictureIds;
    private String idempotencyKey;
}
