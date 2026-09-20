package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("companion_proposal_reaction")
public class CompanionProposalReactionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long proposalId;
    private Long subjectId;
    private String reactionType;
    private Date createTime;
}
