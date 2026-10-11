package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("model_vision_preference")
public class ModelVisionPreferenceEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long subjectId;
    private Boolean forcedSearch;
    private Date createTime;
    private Date updateTime;
}
