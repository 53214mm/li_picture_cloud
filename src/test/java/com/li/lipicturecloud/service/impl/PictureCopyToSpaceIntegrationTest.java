package com.li.lipicturecloud.service.impl;

import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant;
import com.li.lipicturecloud.model.entity.Picture;
import com.li.lipicturecloud.model.entity.Space;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.model.enums.PictureReviewStatusEnum;
import com.li.lipicturecloud.model.vo.PictureVO;
import com.li.lipicturecloud.service.PictureService;
import com.li.lipicturecloud.service.SpaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

// 独立内存库：这个测试替换了授权服务，会单独起一个 Spring 上下文，不能和其他测试共用一个库跑 Liquibase。
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:picture_copy;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
@Sql(scripts = "classpath:picture-copy-schema.sql")
class PictureCopyToSpaceIntegrationTest {

    private static final long OWNER = 7701L;
    private static final long VIEWER = 7702L;

    @Autowired private PictureService pictureService;
    @Autowired private SpaceService spaceService;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private SpaceAuthorizationAccessService authorization;

    private User viewer;
    private long spaceId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM picture WHERE userId IN (?, ?)", OWNER, VIEWER);
        jdbc.update("DELETE FROM space WHERE userId = ?", VIEWER);
        viewer = new User();
        viewer.setId(VIEWER);
        viewer.setUserRole("user");
        Space space = new Space();
        space.setSpaceName("我的空间");
        space.setUserId(VIEWER);
        space.setMaxCount(2L);
        space.setMaxSize(1000L);
        space.setTotalCount(0L);
        space.setTotalSize(0L);
        spaceService.save(space);
        spaceId = space.getId();
    }

    private long publicPicture(int reviewStatus, String url) {
        Picture picture = new Picture();
        picture.setUrl(url);
        picture.setThumbnailUrl(url + "?thumb");
        picture.setName("芙兰朵露");
        picture.setTags("[\"东方\"]");
        picture.setPicSize(300L);
        picture.setPicWidth(474);
        picture.setPicHeight(592);
        picture.setPicFormat("webp");
        picture.setUserId(OWNER);
        picture.setReviewStatus(reviewStatus);
        pictureService.save(picture);
        return picture.getId();
    }

    @Test
    void addsAReviewedPublicPictureToTheViewersSpaceAndCountsTheQuota() {
        long source = publicPicture(PictureReviewStatusEnum.PASS.getValue(), "https://cdn.test/flandre.webp");

        PictureVO copy = pictureService.copyPictureToSpace(source, spaceId, viewer);

        assertThat(copy.getId()).isNotEqualTo(source);
        Picture saved = pictureService.getById(copy.getId());
        assertThat(saved.getUrl()).isEqualTo("https://cdn.test/flandre.webp");
        assertThat(saved.getSpaceId()).isEqualTo(spaceId);
        assertThat(saved.getUserId()).isEqualTo(VIEWER);
        assertThat(saved.getTags()).isEqualTo("[\"东方\"]");
        assertThat(saved.getReviewStatus()).isEqualTo(PictureReviewStatusEnum.PASS.getValue());
        Space space = spaceService.getById(spaceId);
        assertThat(space.getTotalCount()).isEqualTo(1L);
        assertThat(space.getTotalSize()).isEqualTo(300L);
        verify(authorization).checkSpaceForUser(SpaceUserPermissionConstant.PICTURE_UPLOAD, spaceId, VIEWER);

        // 同一张图不会被重复加进同一个空间。
        assertThatThrownBy(() -> pictureService.copyPictureToSpace(source, spaceId, viewer))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已经在这个空间");
    }

    @Test
    void refusesPicturesTheViewerCannotSeeOrWhenTheSpaceIsFull() {
        long unreviewed = publicPicture(PictureReviewStatusEnum.REVIEWING.getValue(), "https://cdn.test/pending.webp");
        assertThatThrownBy(() -> pictureService.copyPictureToSpace(unreviewed, spaceId, viewer))
                .isInstanceOf(BusinessException.class).hasMessageContaining("图片不存在");

        long big = publicPicture(PictureReviewStatusEnum.PASS.getValue(), "https://cdn.test/big.webp");
        jdbc.update("UPDATE picture SET picSize = 2000 WHERE id = ?", big);
        assertThatThrownBy(() -> pictureService.copyPictureToSpace(big, spaceId, viewer))
                .isInstanceOf(BusinessException.class).hasMessageContaining("空间大小不足");
        assertThat(spaceService.getById(spaceId).getTotalCount()).isZero();
    }

    @Test
    void refusesWithoutUploadPermissionOnTheTargetSpace() {
        long source = publicPicture(PictureReviewStatusEnum.PASS.getValue(), "https://cdn.test/other.webp");
        doThrow(new BusinessException(com.li.lipicturecloud.exception.ErrorCode.NO_AUTH_ERROR, "缺少权限"))
                .when(authorization).checkSpaceForUser(eq(SpaceUserPermissionConstant.PICTURE_UPLOAD), anyLong(), anyLong());

        assertThatThrownBy(() -> pictureService.copyPictureToSpace(source, spaceId, viewer))
                .isInstanceOf(BusinessException.class).hasMessageContaining("缺少权限");
        assertThat(spaceService.getById(spaceId).getTotalCount()).isZero();
    }
}
