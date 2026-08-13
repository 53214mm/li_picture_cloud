package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

/**
 * 一个用户在一个上海自然日内的视觉请求次数。
 */
@Data
@TableName("companion_vision_usage")
public class CompanionVisionUsageEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long subjectId;
    private LocalDate usageDate;
    private Integer attempts;
    private Long revision;
    private Date createTime;
    private Date updateTime;
}
