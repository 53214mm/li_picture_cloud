package com.li.lipicturecloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.li.lipicturecloud.model.entity.CompanionVisionUsageEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

/**
 * 额度桶的行锁与条件自增 SQL。条件更新保留了即使锁语义被错误使用时也不超额的最后防线。
 */
public interface CompanionVisionUsageMapper extends BaseMapper<CompanionVisionUsageEntity> {

    @Select("""
            SELECT id, subjectId, usageDate, attempts, revision, createTime, updateTime
            FROM companion_vision_usage
            WHERE subjectId = #{subjectId} AND usageDate = #{usageDate}
            LIMIT 1 FOR UPDATE
            """)
    CompanionVisionUsageEntity selectBySubjectAndUsageDateForUpdate(@Param("subjectId") long subjectId,
                                                                     @Param("usageDate") LocalDate usageDate);

    @Update("""
            UPDATE companion_vision_usage
            SET attempts = attempts + 1, revision = revision + 1, updateTime = #{updateTime}
            WHERE id = #{id} AND revision = #{expectedRevision} AND attempts < #{dailyLimit}
            """)
    int incrementIfBelowLimit(@Param("id") long id, @Param("expectedRevision") long expectedRevision,
                              @Param("dailyLimit") int dailyLimit, @Param("updateTime") java.util.Date updateTime);
}
