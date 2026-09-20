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
             contentUnderstood, providerCode, modelCode, promptVersion, resultSchemaVersion,
             confidence, fallbackReasonCode, balanceVersion, idempotencyKey, correlationId, createTime)
            VALUES
            (#{id}, #{feedingRunId}, #{companionId}, #{pictureId}, #{eventType}, #{lifeExperienceDelta},
             #{traitDeltaJson}, #{skillDeltaJson}, #{snapshotJson}, #{reason}, #{nutritionMode},
             #{contentUnderstood}, #{providerCode}, #{modelCode}, #{promptVersion}, #{resultSchemaVersion},
             #{confidence}, #{fallbackReasonCode}, #{balanceVersion}, #{idempotencyKey}, #{correlationId}, #{createTime})
            """)
    int insert(CompanionGrowthRecordEntity row);

    @Select("""
            SELECT id, feedingRunId, companionId, pictureId, eventType, lifeExperienceDelta,
                   traitDeltaJson, skillDeltaJson, snapshotJson, reason, nutritionMode,
                   contentUnderstood, providerCode, modelCode, promptVersion, resultSchemaVersion,
                   confidence, fallbackReasonCode, balanceVersion, idempotencyKey, correlationId, createTime
            FROM companion_growth_record WHERE id = #{id}
            """)
    CompanionGrowthRecordEntity selectById(@Param("id") long id);

    @Select("""
            SELECT id, feedingRunId, companionId, pictureId, eventType, lifeExperienceDelta,
                   traitDeltaJson, skillDeltaJson, snapshotJson, reason, nutritionMode,
                   contentUnderstood, providerCode, modelCode, promptVersion, resultSchemaVersion,
                   confidence, fallbackReasonCode, balanceVersion, idempotencyKey, correlationId, createTime
            FROM companion_growth_record WHERE feedingRunId = #{feedingRunId}
            """)
    CompanionGrowthRecordEntity selectByFeedingRunId(@Param("feedingRunId") long feedingRunId);

    @Select("""
            SELECT id, feedingRunId, companionId, pictureId, eventType, lifeExperienceDelta,
                   traitDeltaJson, skillDeltaJson, snapshotJson, reason, nutritionMode,
                   contentUnderstood, providerCode, modelCode, promptVersion, resultSchemaVersion,
                   confidence, fallbackReasonCode, balanceVersion, idempotencyKey, correlationId, createTime
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
            SELECT COUNT(*) FROM companion_growth_record
            WHERE companionId = #{companionId} AND createTime >= #{since}
            """)
    long countSince(@Param("companionId") long companionId, @Param("since") Date since);

    @Select("""
            SELECT COUNT(*) FROM companion_growth_record
            WHERE companionId = #{companionId} AND eventType = 'PICTURE_FED'
              AND MONTH(createTime) = #{month} AND DAY(createTime) = #{day}
              AND YEAR(createTime) < YEAR(CURRENT_TIMESTAMP)
            """)
    long countAnniversaryFeeds(@Param("companionId") long companionId,
                               @Param("month") int month, @Param("day") int day);

    @Select("""
            SELECT COALESCE(SUM(lifeExperienceDelta), 0)
            FROM companion_growth_record
            WHERE companionId = #{companionId} AND pictureId = #{pictureId}
              AND eventType = 'PICTURE_REVISITED'
            """)
    long sumRevisitExperience(@Param("companionId") long companionId, @Param("pictureId") long pictureId);

    @Select("""
            SELECT pictureId FROM companion_growth_record
            WHERE companionId = #{companionId} AND eventType = 'PICTURE_FED'
            GROUP BY pictureId
            ORDER BY MAX(createTime) DESC, MAX(id) DESC
            LIMIT #{limit}
            """)
    List<Long> selectRecentFedPictureIds(@Param("companionId") long companionId,
                                         @Param("limit") int limit);
}
