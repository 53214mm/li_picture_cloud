package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("companion_chat_episode")
public class CompanionChatEpisodeEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companionId;
    private Long subjectId;
    private Long firstMessageId;
    private Long lastMessageId;
    private Date startedTime;
    private Date endedTime;
    private String summary;
    private String modelProvider;
    private String modelCode;
    private Date createTime;
}
