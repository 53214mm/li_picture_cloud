package com.li.lipicturecloud.application.companion;

import java.util.Collection;
import java.util.Map;

/**
 * 读取图片的名字、分类和标签。调用方必须只传入已通过来源授权检查的图片。
 */
public interface PictureLabelProvider {

    Map<Long, PictureLabel> labels(Collection<Long> pictureIds);
}
