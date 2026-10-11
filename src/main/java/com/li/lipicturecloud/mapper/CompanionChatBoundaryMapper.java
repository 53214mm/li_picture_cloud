package com.li.lipicturecloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.li.lipicturecloud.model.entity.CompanionChatBoundaryEntity;

public interface CompanionChatBoundaryMapper extends BaseMapper<CompanionChatBoundaryEntity> {
    @org.apache.ibatis.annotations.Update("UPDATE companion_chat_boundary SET chatEpoch = chatEpoch + 1 WHERE subjectId = #{subjectId}")
    int advanceEpoch(long subjectId);
    @org.apache.ibatis.annotations.Select("SELECT chatEpoch FROM companion_chat_boundary WHERE subjectId = #{subjectId}")
    Long epoch(long subjectId);
}
