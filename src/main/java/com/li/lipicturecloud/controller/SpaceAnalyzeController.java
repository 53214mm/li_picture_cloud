package com.li.lipicturecloud.controller;

import com.li.lipicturecloud.common.BaseResponse;
import com.li.lipicturecloud.common.ResultUtils;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.exception.ThrowUtils;
import com.li.lipicturecloud.model.dto.space.analyze.*;
import com.li.lipicturecloud.model.entity.Space;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.model.vo.space.analyze.*;
import com.li.lipicturecloud.service.SpaceAnalyzeService;
import com.li.lipicturecloud.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 空间/图库分析控制器
 * <p>
 * 提供空间使用概览、分类/标签/大小分布、上传趋势、空间排行等多维度分析能力。
 * 所有接口使用 POST 方式，参数通过 JSON 请求体传递。
 * <p>
 * 权限规则：
 * <ul>
 *   <li>查询自己空间：仅空间所有者或管理员</li>
 *   <li>查询公共图库 / 全部空间：仅管理员</li>
 * </ul>
 */
@RestController
@RequestMapping("/space/analyze")
public class SpaceAnalyzeController {

    @Resource
    private SpaceAnalyzeService spaceAnalyzeService;

    @Resource
    private UserService userService;

    /**
     * 获取空间使用概览——已用容量/数量、使用率
     * <p>
     * 根据请求参数自动区分三种模式：
     * <ol>
     *   <li>指定 spaceId → 返回该空间的容量和数量使用率（百分比由后端计算）</li>
     *   <li>queryPublic=true → 返回公共图库统计（管理员，无上限）</li>
     *   <li>queryAll=true → 返回全平台统计（管理员，无上限）</li>
     * </ol>
     */
    @PostMapping("/usage")
    public BaseResponse<SpaceUsageAnalyzeResponse> getSpaceUsageAnalyze(
            @RequestBody SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest,
            HttpServletRequest request
    ) {
        ThrowUtils.throwIf(spaceUsageAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUserEntity(request);
        SpaceUsageAnalyzeResponse spaceUsageAnalyze = spaceAnalyzeService.getSpaceUsageAnalyze(spaceUsageAnalyzeRequest, loginUser);
        return ResultUtils.success(spaceUsageAnalyze);
    }

    /**
     * 图片分类分析——按 category 分组统计数量和总大小
     * <p>
     * 响应为 {@link SpaceCategoryAnalyzeResponse} 列表，适合饼图/环形图展示各分类占比。
     */
    @PostMapping("/category")
    public BaseResponse<List<SpaceCategoryAnalyzeResponse>> getSpaceCategoryAnalyze(
            @RequestBody SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(spaceCategoryAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUserEntity(request);
        List<SpaceCategoryAnalyzeResponse> resultList = spaceAnalyzeService.getSpaceCategoryAnalyze(spaceCategoryAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }

    /**
     * 图片标签分析——将所有图片的 JSON 标签数组展开后统计使用次数
     * <p>
     * 结果按使用次数降序排列，适合柱状图展示热门标签 Top N。
     * 数据库中 tags 存储为 {@code ["标签1","标签2"]} 格式，后端负责解析合并。
     */
    @PostMapping("/tag")
    public BaseResponse<List<SpaceTagAnalyzeResponse>> getSpaceTagAnalyze(
            @RequestBody SpaceTagAnalyzeRequest spaceTagAnalyzeRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(spaceTagAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUserEntity(request);
        List<SpaceTagAnalyzeResponse> resultList = spaceAnalyzeService.getSpaceTagAnalyze(spaceTagAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }

    /**
     * 图片大小分布分析——按文件大小分段统计图片数量
     * <p>
     * 分段范围：{@code <100KB / 100KB-500KB / 500KB-1MB / >1MB}，适合饼图展示。
     */
    @PostMapping("/size")
    public BaseResponse<List<SpaceSizeAnalyzeResponse>> getSpaceSizeAnalyze(
            @RequestBody SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUserEntity(request);
        List<SpaceSizeAnalyzeResponse> resultList = spaceAnalyzeService.getSpaceSizeAnalyze(spaceSizeAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }

    /**
     * 用户上传趋势分析——按时间维度统计上传数量
     * <p>
     * 支持三种时间维度（通过 {@code timeDimension} 指定）：
     * <ul>
     *   <li>{@code day} — 按日聚合，period 格式 {@code YYYY-MM-DD}</li>
     *   <li>{@code week} — 按周聚合，period 为 MySQL YEARWEEK 值</li>
     *   <li>{@code month} — 按月聚合，period 格式 {@code YYYY-MM}</li>
     * </ul>
     * 可选传入 {@code userId} 筛选特定用户。
     */
    @PostMapping("/user")
    public BaseResponse<List<SpaceUserAnalyzeResponse>> getSpaceUserAnalyze(
            @RequestBody SpaceUserAnalyzeRequest spaceUserAnalyzeRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUserEntity(request);
        List<SpaceUserAnalyzeResponse> resultList = spaceAnalyzeService.getSpaceUserAnalyze(spaceUserAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }

    /**
     * 空间使用排行——按 totalSize 降序取前 N 名
     * <p>
     * 仅管理员可访问。返回 Space 实体列表（含 id/spaceName/userId/totalSize）。
     */
    @PostMapping("/rank")
    public BaseResponse<List<Space>> getSpaceRankAnalyze(
            @RequestBody SpaceRankAnalyzeRequest spaceRankAnalyzeRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(spaceRankAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUserEntity(request);
        List<Space> resultList = spaceAnalyzeService.getSpaceRankAnalyze(spaceRankAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }
}
