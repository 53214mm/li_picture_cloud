package com.li.lipicturecloud.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.li.lipicturecloud.model.dto.spaceuser.SpaceUserAddRequest;
import com.li.lipicturecloud.model.dto.spaceuser.SpaceUserEditRequest;
import com.li.lipicturecloud.model.dto.spaceuser.SpaceUserQueryRequest;
import com.li.lipicturecloud.model.entity.SpaceUser;
import com.baomidou.mybatisplus.extension.service.IService;
import com.li.lipicturecloud.model.vo.SpaceUserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
* @author MECHREVO
* @description 针对表【space_user(空间用户关联)】的数据库操作Service
* @createDate 2026-07-27 16:22:26
*/
public interface SpaceUserService extends IService<SpaceUser> {

    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    boolean editSpaceUser(SpaceUserEditRequest spaceUserEditRequest);

    boolean deleteSpaceUser(long id);

    void validSpaceUser(SpaceUser spaceUser, boolean add);

    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);

    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);
}
