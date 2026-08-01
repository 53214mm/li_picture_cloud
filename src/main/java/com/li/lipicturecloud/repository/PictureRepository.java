package com.li.lipicturecloud.repository;

import com.li.lipicturecloud.model.entity.Picture;

import java.util.Optional;

/**
 * 图片持久化边界。上层只表达意图，不依赖 MyBatis-Plus 的具体 API。
 */
public interface PictureRepository {

    Optional<Picture> findById(long pictureId);

    boolean add(Picture picture);

    boolean update(Picture picture);

    boolean removeById(long pictureId);
}
