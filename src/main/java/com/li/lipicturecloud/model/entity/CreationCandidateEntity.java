package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("creation_candidate")
public class CreationCandidateEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long taskId;
    private Integer seq;
    private String text;
    private Date createdTime;
}
