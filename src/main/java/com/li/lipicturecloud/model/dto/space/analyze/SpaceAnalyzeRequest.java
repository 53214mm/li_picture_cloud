package com.li.lipicturecloud.model.dto.space.analyze;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用空间分析请求基类
 * <p>
 * 查询范围三选一：
 * <ul>
 *   <li>{@code spaceId} — 指定私有空间</li>
 *   <li>{@code queryPublic = true} — 公共图库（spaceId IS NULL 的图片）</li>
 *   <li>{@code queryAll = true} — 全平台所有图片（仅管理员）</li>
 * </ul>
 * 子类继承此类，按需添加自身特有参数（如 timeDimension、userId）。
 */
@Data
public class SpaceAnalyzeRequest implements Serializable {

    /** 空间 ID（查询指定私有空间时使用） */
    private Long spaceId;

    /** 是否查询公共图库（spaceId IS NULL） */
    private boolean queryPublic;

    /** 是否查询全部空间（仅管理员，忽略 spaceId 和 queryPublic） */
    private boolean queryAll;

    private static final long serialVersionUID = 1L;
}
