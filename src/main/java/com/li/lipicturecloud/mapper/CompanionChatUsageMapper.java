package com.li.lipicturecloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.li.lipicturecloud.model.entity.CompanionChatUsageEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

public interface CompanionChatUsageMapper extends BaseMapper<CompanionChatUsageEntity> {

    @Select("SELECT id, subjectId, usageDate, attempts, revision, createTime, updateTime "
            + "FROM companion_chat_usage WHERE subjectId = #{subjectId} AND usageDate = #{usageDate} FOR UPDATE")
    CompanionChatUsageEntity selectBySubjectAndUsageDateForUpdate(@Param("subjectId") long subjectId,
                                                                  @Param("usageDate") LocalDate usageDate);

    @Update("UPDATE companion_chat_usage SET attempts = attempts + 1, revision = revision + 1, "
            + "updateTime = #{now} WHERE id = #{id} AND revision = #{revision} AND attempts < #{dailyLimit}")
    int incrementIfBelowLimit(@Param("id") long id, @Param("revision") long revision,
                              @Param("dailyLimit") int dailyLimit, @Param("now") java.util.Date now);
}
