package com.li.lipicturecloud.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 把一张能看到的图片添加到自己的空间。
 */
@Data
public class PictureCopyRequest implements Serializable {

    /** 要添加的图片 */
    private Long pictureId;

    /** 目标空间 */
    private Long spaceId;

    private static final long serialVersionUID = 1L;
}
