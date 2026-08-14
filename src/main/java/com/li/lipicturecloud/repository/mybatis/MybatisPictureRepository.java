package com.li.lipicturecloud.repository.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.lipicturecloud.domain.picture.PictureAsset;
import com.li.lipicturecloud.domain.picture.PictureAssetRepository;
import com.li.lipicturecloud.mapper.PictureMapper;
import com.li.lipicturecloud.model.entity.Picture;
import com.li.lipicturecloud.repository.PictureRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisPictureRepository implements PictureRepository, PictureAssetRepository {

    private final PictureMapper pictureMapper;

    public MybatisPictureRepository(PictureMapper pictureMapper) {
        this.pictureMapper = pictureMapper;
    }

    @Override
    public Optional<Picture> findById(long pictureId) {
        return Optional.ofNullable(pictureMapper.selectById(pictureId));
    }

    @Override
    public Optional<PictureAsset> findAssetById(long pictureId) {
        return findById(pictureId).map(picture ->
                new PictureAsset(picture.getId(), picture.getUserId(), picture.getSpaceId()));
    }

    @Override
    public long countRecentInSpace(long spaceId, Instant since) {
        // 逻辑删除由 MyBatis-Plus 全局配置自动过滤。
        return pictureMapper.selectCount(new LambdaQueryWrapper<Picture>()
                .eq(Picture::getSpaceId, spaceId)
                .ge(Picture::getCreateTime, Date.from(Objects.requireNonNull(since, "since"))));
    }

    @Override
    public boolean add(Picture picture) {
        return pictureMapper.insert(picture) == 1;
    }

    @Override
    public boolean update(Picture picture) {
        return pictureMapper.updateById(picture) == 1;
    }

    @Override
    public boolean removeById(long pictureId) {
        return pictureMapper.deleteById(pictureId) == 1;
    }
}
