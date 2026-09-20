package com.li.lipicturecloud.model.dto.recipe;

import lombok.Data;

/**
 * 配方创建请求：名称即可，定义通过版本发布补充。
 */
@Data
public class RecipeCreateRequest {
    private String name;
}
