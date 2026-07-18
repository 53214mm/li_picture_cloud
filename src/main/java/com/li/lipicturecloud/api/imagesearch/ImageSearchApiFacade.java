package com.li.lipicturecloud.api.imagesearch;

import com.li.lipicturecloud.api.imagesearch.model.ImageSearchResult;
import com.li.lipicturecloud.api.imagesearch.sub.GetImagePageUrlApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 以图搜图门面（Bing 实现）
 * <p>
 * 流程：图片 URL → Bing 反向搜索 → Jsoup 解析 → 返回相似图片列表
 */
@Slf4j
@Component
public class ImageSearchApiFacade {

    /**
     * 以图搜图
     *
     * @param imageUrl 图片 URL
     * @return 相似图片列表（thumbUrl + fromUrl）
     */
    public List<ImageSearchResult> searchByImage(String imageUrl) {
        return GetImagePageUrlApi.searchByImage(imageUrl);
    }
}
