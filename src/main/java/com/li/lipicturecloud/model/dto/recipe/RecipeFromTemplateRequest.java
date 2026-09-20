package com.li.lipicturecloud.model.dto.recipe;

import lombok.Data;

/**
 * 从官方模板创建配方请求：模板码 + 可选自定义名称。
 */
@Data
public class RecipeFromTemplateRequest {
    private String templateCode;
    private String name;
}
