package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("recipe_version")
public class RecipeVersionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long recipeId;
    private Integer version;
    private String whenJson;
    private String ifJson;
    private String thenJson;
    private Date createdTime;
}
