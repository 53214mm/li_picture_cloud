package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("recipe_execution")
public class RecipeExecutionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long recipeId;
    private Integer recipeVersion;
    private Long subjectId;
    private String status;
    private Date triggeredTime;
    private String matchedJson;
    private String quoteJson;
    private Long creationTaskId;
    private String safeErrorCode;
    private Date createdTime;
}
