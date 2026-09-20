package com.li.lipicturecloud.model.dto.recipe;

import lombok.Data;

import java.util.List;

/**
 * 配方试运行/执行请求：本次执行使用的授权图片。
 */
@Data
public class RecipeRunRequest {
    private List<Long> pictureIds;
}
