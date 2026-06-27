package com.li.lipicturecloud.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.lipicturecloud.model.dto.picture.PictureQueryRequest;
import com.li.lipicturecloud.model.dto.picture.PictureReviewRequest;
import com.li.lipicturecloud.model.dto.picture.PictureUploadByBatchRequest;
import com.li.lipicturecloud.model.dto.picture.PictureUploadRequest;
import com.li.lipicturecloud.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.model.vo.UserVO;
import com.li.lipicturecloud.model.vo.PictureVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

/**
* @author MECHREVO
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2026-06-10 17:23:29
*/
public interface PictureService extends IService<Picture> {

    /**
     * 上传图片（统一入口：支持 MultipartFile 文件上传和 String URL 上传）
     *
     * @param inputSource          输入源（MultipartFile 或 String fileUrl）
     * @param pictureUploadRequest 上传请求（可含 id 表示更新）
     * @param loginUser            当前登录用户（完整实体）
     * @return PictureVO
     */
    PictureVO uploadPicture(Object inputSource,
                            PictureUploadRequest pictureUploadRequest,
                            User loginUser);


    /**
     * 获取查询包装器
     *
     * @param pictureQueryRequest
     * @return
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    void validPicture(Picture picture);

    /**
     * 图片审核
     *
     * @param pictureReviewRequest
     * @param loginUser
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);


    void fillReviewParams(Picture picture, User loginUser);

    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return 成功创建的图片数
     */
    Integer uploadPictureByBatch(
            PictureUploadByBatchRequest pictureUploadByBatchRequest,
            User loginUser
    );

}
