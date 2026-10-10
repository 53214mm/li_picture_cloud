package com.li.lipicturecloud.domain.airuntime;

/**
 * 用户的识图联网偏好。没有记录表示没开启。
 */
public interface VisionSearchPreferenceRepository {

    boolean forcedSearch(long subjectId);

    /** 写入（不存在则创建）用户的识图联网偏好。 */
    void save(long subjectId, boolean forcedSearch);
}
