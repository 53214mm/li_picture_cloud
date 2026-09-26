package com.li.lipicturecloud.mapper;

import com.li.lipicturecloud.model.entity.CompanionGrowthRecordEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

/**
 * 伙伴成长记录表的受限 Mapper。
 *
 * <p>这里故意不继承 BaseMapper，只开放插入、查询和统计能力，不暴露 update/delete，
 * 从数据库访问入口强化成长事实“只能追加、不能改写”的约束。</p>
 */
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
    /** 追加一条成长事实。 */
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
    /** 根据喂养执行 id 查询唯一成长事实，用于幂等恢复。 */
    CompanionGrowthRecordEntity selectByFeedingRunId(@Param("feedingRunId") long feedingRunId);

    @Select("""
            SELECT id, feedingRunId, companionId, pictureId, eventType, lifeExperienceDelta,
                   traitDeltaJson, skillDeltaJson, snapshotJson, reason, nutritionMode,
                   contentUnderstood, providerCode, modelCode, promptVersion, resultSchemaVersion,
                   confidence, fallbackReasonCode, balanceVersion, idempotencyKey, correlationId, createTime
            FROM companion_growth_record WHERE companionId = #{companionId}
            ORDER BY createTime DESC, id DESC LIMIT #{limit}
            """)
    /** 查询伙伴最近的成长事实。 */
    List<CompanionGrowthRecordEntity> selectRecent(@Param("companionId") long companionId,
                                                    @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) FROM companion_growth_record
            WHERE companionId = #{companionId} AND pictureId = #{pictureId}
              AND eventType = 'PICTURE_FED'
            """)
    /** 统计指定图片完成过多少次完整喂养。 */
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
              AND YEAR(createTime) < #{year}
            """)
    long countAnniversaryFeeds(@Param("companionId") long companionId,
                               @Param("year") int year, @Param("month") int month,
                               @Param("day") int day);

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
