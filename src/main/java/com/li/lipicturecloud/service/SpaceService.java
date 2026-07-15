package com.li.lipicturecloud.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.lipicturecloud.model.dto.space.SpaceAddRequest;
import com.li.lipicturecloud.model.dto.space.SpaceQueryRequest;
import com.li.lipicturecloud.model.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.model.vo.SpaceVO;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author MECHREVO
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2026-07-14 15:15:00
*/
public interface SpaceService extends IService<Space> {



    /**
     * 获取查询包装器
     *
     * @param spaceQueryRequest
     * @return
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 获取空间视图对象
     * @param space
     * @param request
     * @return
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    /**
     * 获取空间视图对象分页
     * @param spacePage
     * @param request
     * @return
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

    /**
     * 验证空间是否合法
     * @param space
     * @param add 是否是新增操作
     */
    void validSpace(Space space,boolean add);

    /**
     * 根据空间级别填充空间的最大大小和最大数量
     * @param space
     */
    void fillSpaceBySpaceLevel(Space space);

    /**
     * 新增空间
     * @param spaceAddRequest
     * @param loginUser
     * @return
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

}
