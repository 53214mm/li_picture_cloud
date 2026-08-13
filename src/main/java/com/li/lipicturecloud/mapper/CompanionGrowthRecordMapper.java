package com.li.lipicturecloud.mapper;

import com.li.lipicturecloud.model.entity.CompanionGrowthRecordEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

public interface CompanionGrowthRecordMapper {

    @Insert("""
            INSERT INTO companion_growth_record
            (id, feedingRunId, companionId, pictureId, eventType, lifeExperienceDelta,
             traitDeltaJson, skillDeltaJson, snapshotJson, reason, nutritionMode,
             contentUnderstood, balanceVersion, idempotencyKey, correlationId, createTime)
            VALUES
            (#{id}, #{feedingRunId}, #{companionId}, #{pictureId}, #{eventType}, #{lifeExperienceDelta},
             #{traitDeltaJson}, #{skillDeltaJson}, #{snapshotJson}, #{reason}, #{nutritionMode},
             #{contentUnderstood}, #{balanceVersion}, #{idempotencyKey}, #{correlationId}, #{createTime})
            """)
    int insert(CompanionGrowthRecordEntity row);

    @Select("""
            SELECT id, feedingRunId, companionId, pictureId, eventType, lifeExperienceDelta,
                   traitDeltaJson, skillDeltaJson, snapshotJson, reason, nutritionMode,
                   contentUnderstood, balanceVersion, idempotencyKey, correlationId, createTime
            FROM companion_growth_record WHERE id = #{id}
            """)
    CompanionGrowthRecordEntity selectById(@Param("id") long id);

    @Select("""
            SELECT id, feedingRunId, companionId, pictureId, eventType, lifeExperienceDelta,
                   traitDeltaJson, skillDeltaJson, snapshotJson, reason, nutritionMode,
                   contentUnderstood, balanceVersion, idempotencyKey, correlationId, createTime
            FROM companion_growth_record WHERE feedingRunId = #{feedingRunId}
            """)
    CompanionGrowthRecordEntity selectByFeedingRunId(@Param("feedingRunId") long feedingRunId);

    @Select("""
            SELECT id, feedingRunId, companionId, pictureId, eventType, lifeExperienceDelta,
                   traitDeltaJson, skillDeltaJson, snapshotJson, reason, nutritionMode,
                   contentUnderstood, balanceVersion, idempotencyKey, correlationId, createTime
            FROM companion_growth_record WHERE companionId = #{companionId}
            ORDER BY createTime DESC, id DESC LIMIT #{limit}
            """)
    List<CompanionGrowthRecordEntity> selectRecent(@Param("companionId") long companionId,
                                                    @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) FROM companion_growth_record
            WHERE companionId = #{companionId} AND pictureId = #{pictureId}
              AND eventType = 'PICTURE_FED'
            """)
    long countFullFeeds(@Param("companionId") long companionId, @Param("pictureId") long pictureId);

    @Select("""
            SELECT COALESCE(SUM(lifeExperienceDelta), 0)
            FROM companion_growth_record
            WHERE companionId = #{companionId} AND createTime >= #{since}
            """)
    long sumLifeExperienceSince(@Param("companionId") long companionId, @Param("since") Date since);

    @Select("""
            SELECT COALESCE(SUM(lifeExperienceDelta), 0)
            FROM companion_growth_record
            WHERE companionId = #{companionId} AND pictureId = #{pictureId}
              AND eventType = 'PICTURE_REVISITED'
            """)
    long sumRevisitExperience(@Param("companionId") long companionId, @Param("pictureId") long pictureId);
}
