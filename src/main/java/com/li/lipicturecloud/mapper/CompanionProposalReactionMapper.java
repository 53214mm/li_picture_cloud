package com.li.lipicturecloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.li.lipicturecloud.model.entity.CompanionProposalReactionEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;

public interface CompanionProposalReactionMapper extends BaseMapper<CompanionProposalReactionEntity> {

    @Select("SELECT COUNT(*) FROM companion_proposal_reaction "
            + "WHERE subjectId = #{subjectId} AND reactionType = 'SCOLD' AND createTime >= #{since}")
    long countScoldsSince(@Param("subjectId") long subjectId, @Param("since") Date since);
}
