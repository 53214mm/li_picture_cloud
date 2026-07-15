package com.li.lipicturecloud.controller;


import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.li.lipicturecloud.annotation.AuthCheck;
import com.li.lipicturecloud.common.BaseResponse;
import com.li.lipicturecloud.common.DeleteRequest;
import com.li.lipicturecloud.common.ResultUtils;
import com.li.lipicturecloud.config.CosClientConfig;
import com.li.lipicturecloud.manager.CosManager;
import com.li.lipicturecloud.model.entity.Space;
import com.li.lipicturecloud.service.SpaceService;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import com.li.lipicturecloud.constant.UserConstant;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.exception.ThrowUtils;
import com.li.lipicturecloud.model.dto.picture.*;
import com.li.lipicturecloud.model.entity.Picture;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.model.enums.PictureReviewStatusEnum;
import com.li.lipicturecloud.model.vo.PictureTagCategory;
import com.li.lipicturecloud.model.vo.UserVO;
import com.li.lipicturecloud.model.vo.PictureVO;
import com.li.lipicturecloud.service.PictureService;
import com.li.lipicturecloud.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;


@Slf4j
@RestController
@RequestMapping("/picture")
public class PictureController {

    @Resource
    private PictureService pictureService;
    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CosManager cosManager;
    @Resource
    private CosClientConfig cosClientConfig;
    /**
     * 本地缓存
     */
    private final Cache<String, String> LOCAL_CACHE =
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10000L)
                    .expireAfterWrite(5L, TimeUnit.MINUTES)
                    .build();

    /** 缓存版本号（每次图片变更递增，旧版本 key 自然失效） */
    private final LongAdder cacheVersion = new LongAdder();

    /** 清除全部缓存（图片变更时调用，递增版本使 Redis 旧缓存失效） */
    private void clearListCache() {
        cacheVersion.increment();
        LOCAL_CACHE.invalidateAll();
    }


    /**
     * 上传图片（可重新上传）
     */
    @PostMapping("/upload")
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUserEntity(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        clearListCache();
        return ResultUtils.success(pictureVO);
    }
    /**
     * 通过 URL 上传图片（可重新上传）
     */
    @PostMapping("/upload/url")
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUserEntity(request);
        String fileUrl = pictureUploadRequest.getFileUrl();
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        clearListCache();
        return ResultUtils.success(pictureVO);
    }


    /**
     * 删除图片
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUserEntity(request);
        pictureService.deletePicture(deleteRequest.getId(), loginUser);
        clearListCache();
        return ResultUtils.success(true);
    }

    /**
     * 更新图片（仅管理员可用）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        // 数据校验
        pictureService.validPicture(picture);
        // 判断是否存在
        long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 补充审核参数
        User loginUser = userService.getLoginUserEntity(request);
        pictureService.fillReviewParams(picture, loginUser);
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        clearListCache();
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取图片（仅管理员可用）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(picture);
    }

    /**
     * 根据 id 获取图片（封装类）
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 空间权限校验
        Long spaceId = picture.getSpaceId();
        if(spaceId != null) {
            pictureService.checkPictureAuth(userService.getLoginUserEntity(request), picture);
        }
        // 获取封装类
        return ResultUtils.success(pictureService.getPictureVO(picture, request));
    }

    /**
     * 分页获取图片列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 分页获取图片列表（封装类）
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 空间权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        if(spaceId == null) {
            //公开图库
            // 普通用户默认只能查看已过审的数据
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        }else {
            User loginUser = userService.getLoginUserEntity(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
        }
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));

        // 获取封装类
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }

    /**
     * 分页获取图片列表（封装类）
     */
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 空间权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) {
            // 公开图库：只展示已过审数据
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        } else {
            // 私有空间：需要权限校验
            User loginUser = userService.getLoginUserEntity(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
        }

        // 构造查询条件的 MD5 哈希作为缓存键（含版本号，版本递增时旧缓存自然失效）
        long version = cacheVersion.sum();
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes(StandardCharsets.UTF_8));
        String cacheKey = String.format("listPictureVOByPage:v%d:%s", version, hashKey);
        String redisKey = String.format("lipicturecloud:listPictureVOByPage:v%d:%s", version, hashKey);

        // 1. 先查本地缓存（Caffeine）
        String cachedResult = LOCAL_CACHE.getIfPresent(cacheKey);
        if (cachedResult != null) {
            Page<PictureVO> pictureVOPage = JSONUtil.toBean(cachedResult, Page.class);
            return ResultUtils.success(pictureVOPage);
        }

        // 2. 本地缓存未命中，尝试查询 Redis（Redis 不可用时降级跳过）
        try {
            cachedResult = stringRedisTemplate.opsForValue().get(redisKey);
            if (cachedResult != null) {
                LOCAL_CACHE.put(cacheKey, cachedResult);
                Page<PictureVO> pictureVOPage = JSONUtil.toBean(cachedResult, Page.class);
                return ResultUtils.success(pictureVOPage);
            }
        } catch (Exception e) {
            log.warn("Redis 查询失败，降级查询数据库: {}", e.getMessage());
        }

        // 3. 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);

        // 尝试写入两级缓存（Redis 写入失败不影响响应）
        try {
            String jsonResult = JSONUtil.toJsonStr(pictureVOPage);
            int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);
            stringRedisTemplate.opsForValue().set(redisKey, jsonResult, cacheExpireTime, TimeUnit.SECONDS);
            LOCAL_CACHE.put(cacheKey, jsonResult);
        } catch (Exception e) {
            log.warn("Redis 写入失败，仅缓存到本地: {}", e.getMessage());
            LOCAL_CACHE.put(cacheKey, JSONUtil.toJsonStr(pictureVOPage));
        }

        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 编辑图片（给用户使用）
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUserEntity(request);
        pictureService.editPicture(pictureEditRequest, loginUser);
        clearListCache();
        return ResultUtils.success(true);
    }

    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }

    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest,
                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUserEntity(request);
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        clearListCache();
        return ResultUtils.success(true);
    }

    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(
            @RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
            HttpServletRequest request
    ) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUserEntity(request);
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        clearListCache();
        return ResultUtils.success(uploadCount);
    }

    /**
     * 下载图片原图（通过后端代理，解决 COS 跨域问题）
     */
    @GetMapping("/download/{id}")
    public void downloadPicture(@PathVariable long id, HttpServletRequest request,
                                HttpServletResponse response) throws IOException {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);

        // 仅本人或管理员可下载
        UserVO loginUser = userService.getLoginUser(request);
        if (!picture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 优先用原图 URL（原始格式），其次用 url（webp）
        String downloadUrl = picture.getOriginalUrl() != null ? picture.getOriginalUrl() : picture.getUrl();
        String key = downloadUrl.substring(downloadUrl.indexOf(cosClientConfig.getHost()) + cosClientConfig.getHost().length() + 1);

        COSObjectInputStream cosInput = null;
        try {
            COSObject cosObject = cosManager.getObject(key);
            cosInput = cosObject.getObjectContent();
            byte[] bytes = IOUtils.toByteArray(cosInput);

            // 文件名：优先用图片名称，纯数字哈希则用 "图片_"+id；确保扩展名一定有值
            String name = picture.getName();
            if (StrUtil.isBlank(name) || name.matches("[0-9a-zA-Z]+")) {
                name = "图片_" + id;
            }
            String ext = StrUtil.isNotBlank(picture.getPicFormat()) ? picture.getPicFormat() : "png";
            String filename = name + "." + ext;
            // filename 仅允许 ASCII，中文通过 filename*=UTF-8'' 编码传递
            String asciiFallback = "download." + ext;
            String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded);
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("图片下载失败, id={}", id, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "下载失败");
        } finally {
            if (cosInput != null) cosInput.close();
        }
    }




}
