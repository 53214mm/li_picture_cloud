package com.li.lipicturecloud.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.exception.ThrowUtils;
import com.li.lipicturecloud.mapper.SpaceMapper;
import com.li.lipicturecloud.model.dto.space.SpaceAddRequest;
import com.li.lipicturecloud.model.dto.space.SpaceQueryRequest;
import com.li.lipicturecloud.model.dto.space.analyze.*;
import com.li.lipicturecloud.model.entity.Picture;
import com.li.lipicturecloud.model.entity.Space;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.model.enums.SpaceLevelEnum;
import com.li.lipicturecloud.model.vo.SpaceVO;
import com.li.lipicturecloud.model.vo.UserVO;
import com.li.lipicturecloud.model.vo.space.analyze.*;
import com.li.lipicturecloud.service.PictureService;
import com.li.lipicturecloud.service.SpaceAnalyzeService;
import com.li.lipicturecloud.service.SpaceService;
import com.li.lipicturecloud.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 空间/图库分析服务实现
 * <p>
 * 核心分析维度：
 * <ol>
 *   <li><b>使用概览</b> — 已用容量/数量 vs 上限，使用率百分比</li>
 *   <li><b>分类分析</b> — 按 category 分组统计（SQL GROUP BY）</li>
 *   <li><b>标签分析</b> — JSON 标签数组展开后聚合统计</li>
 *   <li><b>大小分布</b> — 按文件大小分段统计（Java 内存分段）</li>
 *   <li><b>上传趋势</b> — 按时间维度（日/周/月）聚合</li>
 *   <li><b>空间排行</b> — 按 totalSize 降序取前 N 名</li>
 * </ol>
 * <p>
 * 请求基类 {@link SpaceAnalyzeRequest} 定义了三种查询范围：
 * {@code spaceId}（指定空间）/ {@code queryPublic}（公共图库）/ {@code queryAll}（全部）。
 * 子类继承后只需声明自身特有字段。
 *
 * @see SpaceAnalyzeRequest
 * @see SpaceAnalyzeService
 */
@Service
public class SpaceAnalyzeServiceImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceAnalyzeService {

    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;
    @Resource
    private PictureService pictureService;

    // ======================== 1. 使用概览 ========================

    /**
     * 获取空间使用分析数据
     * <p>
     * 根据请求参数自动区分三种模式：
     * <ul>
     *   <li><b>指定空间</b>（spaceId）→ 从 Space 实体读取已用量和上限，后端计算百分比</li>
     *   <li><b>公共图库</b>（queryPublic）→ 统计 spaceId IS NULL 的图片总大小和数量</li>
     *   <li><b>全部空间</b>（queryAll）→ 统计所有图片（管理员）</li>
     * </ul>
     * <p>
     * 公共/全部分析场景下 maxSize/maxCount 为 null（无上限），前端可据此展示 "∞"。
     *
     * @param spaceUsageAnalyzeRequest 请求参数（继承 SpaceAnalyzeRequest）
     * @param loginUser                当前登录用户
     * @return 使用概览（含已用量、上限、使用率）
     */
    @Override
    public SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceUsageAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);

        // ---- 分支 A：公共图库 / 全部空间 ----
        if (spaceUsageAnalyzeRequest.isQueryAll() || spaceUsageAnalyzeRequest.isQueryPublic()) {
            // 全部分析仅管理员，公共图库所有用户可访问
            if (spaceUsageAnalyzeRequest.isQueryAll()) {
                ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "无权访问全空间分析");
            }

            // 统计图片总大小和数量
            QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
            queryWrapper.select("picSize");
            // queryPublic 模式：只查 spaceId 为空的图片（公共图库）
            if (!spaceUsageAnalyzeRequest.isQueryAll()) {
                queryWrapper.isNull("spaceId");
            }
            List<Object> pictureObjList = pictureService.getBaseMapper().selectObjs(queryWrapper);
            long usedSize = pictureObjList.stream()
                    .mapToLong(result -> result instanceof Long ? (Long) result : 0)
                    .sum();
            long usedCount = pictureObjList.size();

            // 公共图库无容量上限，百分比标记为 null
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(usedSize);
            spaceUsageAnalyzeResponse.setUsedCount(usedCount);
            spaceUsageAnalyzeResponse.setMaxSize(null);
            spaceUsageAnalyzeResponse.setSizeUsageRatio(null);
            spaceUsageAnalyzeResponse.setMaxCount(null);
            spaceUsageAnalyzeResponse.setCountUsageRatio(null);
            return spaceUsageAnalyzeResponse;
        }

        // ---- 分支 B：指定私有空间 ----
        Long spaceId = spaceUsageAnalyzeRequest.getSpaceId();
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);

        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");

        // 权限校验：仅所有者或管理员
        spaceService.checkSpaceAuth(loginUser, space);

        // 后端计算使用率百分比 —— 前端无需额外计算，直接展示
        SpaceUsageAnalyzeResponse response = new SpaceUsageAnalyzeResponse();
        response.setUsedSize(space.getTotalSize());
        response.setMaxSize(space.getMaxSize());
        double sizeUsageRatio = NumberUtil.round(
                space.getTotalSize() * 100.0 / space.getMaxSize(), 2).doubleValue();
        response.setSizeUsageRatio(sizeUsageRatio);
        response.setUsedCount(space.getTotalCount());
        response.setMaxCount(space.getMaxCount());
        double countUsageRatio = NumberUtil.round(
                space.getTotalCount() * 100.0 / space.getMaxCount(), 2).doubleValue();
        response.setCountUsageRatio(countUsageRatio);
        return response;
    }

    // ======================== 2. 分类分析 ========================

    /**
     * 按图片分类（category）分组统计
     * <p>
     * 使用 SQL GROUP BY 在数据库层聚合，避免拉取全量数据到内存。
     * category 为 null 的记录归入 "未分类"。
     *
     * @return 每个分类的图片数量和总大小，适合饼图/环形图展示
     */
    @Override
    public List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(
            SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceCategoryAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        checkSpaceAnalyzeAuth(spaceCategoryAnalyzeRequest, loginUser);

        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceCategoryAnalyzeRequest, queryWrapper);

        // SQL: SELECT category, COUNT(*) AS count, SUM(picSize) AS totalSize GROUP BY category
        queryWrapper.select("category AS category", "COUNT(*) AS count", "SUM(picSize) AS totalSize")
                .groupBy("category");

        return pictureService.getBaseMapper().selectMaps(queryWrapper)
                .stream()
                .map(result -> {
                    String category = result.get("category") != null
                            ? result.get("category").toString() : "未分类";
                    Long count = ((Number) result.get("count")).longValue();
                    Long totalSize = ((Number) result.get("totalSize")).longValue();
                    return new SpaceCategoryAnalyzeResponse(category, count, totalSize);
                })
                .collect(Collectors.toList());
    }

    // ======================== 3. 标签分析 ========================

    /**
     * 图片标签使用频次分析
     * <p>
     * 实现关键：数据库中 {@code tags} 字段存储为 JSON 数组字符串
     * （如 {@code ["风景","城市","夜景"]}），需要：
     * <ol>
     *   <li>查询所有符合条件的 tags 字段</li>
     *   <li>逐行反序列化为 {@code List<String>}</li>
     *   <li>flatMap 将所有标签合并为一个流</li>
     *   <li>groupingBy 分组统计次数</li>
     *   <li>按使用次数降序排列</li>
     * </ol>
     *
     * @return 标签列表（按 count 降序），适合柱状图展示热门标签 Top N
     */
    @Override
    public List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(
            SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceTagAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        checkSpaceAnalyzeAuth(spaceTagAnalyzeRequest, loginUser);

        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceTagAnalyzeRequest, queryWrapper);

        // 只查 tags 列，减少数据传输量
        queryWrapper.select("tags");
        List<String> tagsJsonList = pictureService.getBaseMapper().selectObjs(queryWrapper)
                .stream()
                .filter(ObjUtil::isNotNull)
                .map(Object::toString)
                .collect(Collectors.toList());

        // flatMap 展开所有标签 → groupingBy 聚合 → 按 count 降序
        Map<String, Long> tagCountMap = tagsJsonList.stream()
                .flatMap(tagsJson -> JSONUtil.toList(tagsJson, String.class).stream())
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));

        return tagCountMap.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue())) // 降序
                .map(entry -> new SpaceTagAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    // ======================== 4. 图片大小分布 ========================

    /**
     * 按文件大小分段统计图片数量
     * <p>
     * 分段在 Java 内存中完成（先查询所有 picSize，再遍历归类）。
     * 分段标准：
     * <ul>
     *   <li>{@code <100KB} — 小图</li>
     *   <li>{@code 100KB-500KB} — 中等</li>
     *   <li>{@code 500KB-1MB} — 较大</li>
     *   <li>{@code >1MB} — 大图</li>
     * </ul>
     * <p>
     * 使用 {@link LinkedHashMap} 保证分段顺序（饼图图例按此顺序渲染）。
     *
     * @return 每个大小范围的图片数量
     */
    @Override
    public List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(
            SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        checkSpaceAnalyzeAuth(spaceSizeAnalyzeRequest, loginUser);

        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceSizeAnalyzeRequest, queryWrapper);

        // 只查询 picSize 列
        queryWrapper.select("picSize");
        List<Long> picSizes = pictureService.getBaseMapper().selectObjs(queryWrapper)
                .stream()
                .map(size -> ((Number) size).longValue())
                .collect(Collectors.toList());

        // LinkedHashMap 保证分段顺序（渲染时图例顺序稳定）
        Map<String, Long> sizeRanges = new LinkedHashMap<>();
        sizeRanges.put("<100KB", picSizes.stream().filter(size -> size < 100 * 1024).count());
        sizeRanges.put("100KB-500KB", picSizes.stream()
                .filter(size -> size >= 100 * 1024 && size < 500 * 1024).count());
        sizeRanges.put("500KB-1MB", picSizes.stream()
                .filter(size -> size >= 500 * 1024 && size < 1024 * 1024).count());
        sizeRanges.put(">1MB", picSizes.stream().filter(size -> size >= 1024 * 1024).count());

        return sizeRanges.entrySet().stream()
                .map(entry -> new SpaceSizeAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    // ======================== 5. 上传趋势分析 ========================

    /**
     * 按时间维度统计用户上传趋势
     * <p>
     * 三种时间维度通过 MySQL 函数实现：
     * <ul>
     *   <li>{@code day} — {@code DATE_FORMAT(createTime, '%Y-%m-%d')}</li>
     *   <li>{@code week} — {@code YEARWEEK(createTime)}</li>
     *   <li>{@code month} — {@code DATE_FORMAT(createTime, '%Y-%m')}</li>
     * </ul>
     * <p>
     * 可选传入 {@code userId} 筛选特定用户的上传数据。
     *
     * @param spaceUserAnalyzeRequest 请求参数（含 timeDimension + 可选 userId）
     * @param loginUser               当前登录用户
     * @return 时间序列数据（period → count），适合折线图展示
     */
    @Override
    public List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(
            SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        checkSpaceAnalyzeAuth(spaceUserAnalyzeRequest, loginUser);

        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        // 可选：按 userId 筛选
        Long userId = spaceUserAnalyzeRequest.getUserId();
        queryWrapper.eq(ObjUtil.isNotNull(userId), "userId", userId);
        fillAnalyzeQueryWrapper(spaceUserAnalyzeRequest, queryWrapper);

        // ★ 白名单校验时间维度，防止非法值拼接 SQL
        String timeDimension = spaceUserAnalyzeRequest.getTimeDimension();
        ThrowUtils.throwIf(!Set.of("day", "week", "month").contains(timeDimension),
                ErrorCode.PARAMS_ERROR, "不支持的时间维度: " + timeDimension);

        // 根据维度选择 MySQL 日期函数
        switch (timeDimension) {
            case "day":
                // 输出格式: 2026-07-20
                queryWrapper.select("DATE_FORMAT(createTime, '%Y-%m-%d') AS period", "COUNT(*) AS count");
                break;
            case "week":
                // 输出格式: 202630（年份+周数）
                queryWrapper.select("YEARWEEK(createTime) AS period", "COUNT(*) AS count");
                break;
            case "month":
                // 输出格式: 2026-07
                queryWrapper.select("DATE_FORMAT(createTime, '%Y-%m') AS period", "COUNT(*) AS count");
                break;
            default:
                // 理论上不会走到这里（前面已有白名单校验）
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的时间维度");
        }

        // GROUP BY period，ORDER BY period ASC（时间升序）
        queryWrapper.groupBy("period").orderByAsc("period");

        List<Map<String, Object>> queryResult = pictureService.getBaseMapper().selectMaps(queryWrapper);
        return queryResult.stream()
                .map(result -> {
                    String period = result.get("period").toString();
                    Long count = ((Number) result.get("count")).longValue();
                    return new SpaceUserAnalyzeResponse(period, count);
                })
                .collect(Collectors.toList());
    }

    // ======================== 6. 空间排行 ========================

    /**
     * 空间使用排行——按 totalSize 降序取前 N 名
     * <p>
     * 仅管理员可访问。只查询 id/spaceName/userId/totalSize 四列减少数据传输。
     * topN 范围限制 1-100，防止一次性拉取全量数据。
     *
     * @param spaceRankAnalyzeRequest 请求参数（含 topN，默认 10）
     * @param loginUser               当前登录用户（需管理员）
     * @return Space 实体列表（限四列），按 totalSize 降序
     */
    @Override
    public List<Space> getSpaceRankAnalyze(
            SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceRankAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);

        // 仅管理员可查看全平台空间排行
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "无权查看空间排行");

        // ★ topN 范围校验：防止传入 0 或超大值
        int topN = spaceRankAnalyzeRequest.getTopN() != null
                ? spaceRankAnalyzeRequest.getTopN() : 10;
        ThrowUtils.throwIf(topN <= 0 || topN > 100,
                ErrorCode.PARAMS_ERROR, "topN 取值范围 1-100");

        // 只查询展示需要的列，减少 IO
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "spaceName", "userId", "totalSize")
                .orderByDesc("totalSize")
                .last("LIMIT " + topN); // topN 是 int 类型，无 SQL 注入风险

        return spaceService.list(queryWrapper);
    }

    // ======================== 内部工具方法 ========================

    /**
     * 校验分析请求的访问权限
     * <p>
     * 规则：
     * <ul>
     *   <li>{@code queryAll} 或 {@code queryPublic} → 仅管理员</li>
     *   <li>指定 {@code spaceId} → 空间所有者或管理员（调用 {@link SpaceService#checkSpaceAuth}）</li>
     * </ul>
     *
     * @param spaceAnalyzeRequest 分析请求（含查询范围参数）
     * @param loginUser           当前登录用户
     */
    private void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser) {
        if (spaceAnalyzeRequest.isQueryAll()) {
            // 全空间 → 仅管理员
            ThrowUtils.throwIf(!userService.isAdmin(loginUser),
                    ErrorCode.NO_AUTH_ERROR, "无权访问全空间分析");
        } else if (spaceAnalyzeRequest.isQueryPublic()) {
            // 公共图库 → 所有登录用户可查看
            // 无需额外权限校验
        } else {
            // 指定私有空间 → 所有者或管理员
            Long spaceId = spaceAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            spaceService.checkSpaceAuth(loginUser, space);
        }
    }

    /**
     * 根据分析范围填充 WHERE 条件
     * <p>
     * 三种范围对应的 SQL 条件：
     * <ul>
     *   <li>{@code queryAll} → 不加任何条件（全表）</li>
     *   <li>{@code queryPublic} → {@code WHERE spaceId IS NULL}</li>
     *   <li>{@code spaceId} 非空 → {@code WHERE spaceId = ?}</li>
     * </ul>
     * 如果三种条件都不满足，抛出参数错误异常。
     * <p>
     * 设计为 static 方法以强调无副作用——只修改传入的 queryWrapper 对象。
     *
     * @param spaceAnalyzeRequest 分析请求
     * @param queryWrapper        MyBatis-Plus 查询包装器（会被修改）
     * @throws BusinessException 如果未指定任何查询范围
     */
    private static void fillAnalyzeQueryWrapper(SpaceAnalyzeRequest spaceAnalyzeRequest,
                                                 QueryWrapper<Picture> queryWrapper) {
        if (spaceAnalyzeRequest.isQueryAll()) {
            // 查询全部：不加任何条件
            return;
        }
        if (spaceAnalyzeRequest.isQueryPublic()) {
            // 查询公共图库：spaceId 为空的图片
            queryWrapper.isNull("spaceId");
            return;
        }
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        if (spaceId != null) {
            // 查询指定空间
            queryWrapper.eq("spaceId", spaceId);
            return;
        }
        // 三种条件都不满足 → 参数错误
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "未指定查询范围");
    }
}
