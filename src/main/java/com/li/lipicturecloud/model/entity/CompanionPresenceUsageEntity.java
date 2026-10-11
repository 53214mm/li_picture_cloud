package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

@Data
@TableName("companion_presence_usage")
public class CompanionPresenceUsageEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long subjectId;
    private LocalDate usageDate;
    private Integer attempts;
    private Long revision;
    private Date createTime;
    private Date updateTime;
}
