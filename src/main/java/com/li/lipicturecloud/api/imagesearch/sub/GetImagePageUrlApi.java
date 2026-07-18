package com.li.lipicturecloud.api.imagesearch.sub;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.li.lipicturecloud.api.imagesearch.model.ImageSearchResult;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

/**
 * Bing 以图搜图
 * <p>
 * 流程：构造 imgurl 参数 URL → Jsoup 解析 → 提取相似图片列表
 */
@Slf4j
public class GetImagePageUrlApi {

    private static final String BING_SEARCH_URL = "https://www.bing.com/images/search";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            + " (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /**
     * 以图搜图 — 返回 Bing 找到的相似图片列表
     *
     * @param imageUrl 图片 URL
     * @return 相似图片列表（thumbUrl 缩略图 + fromUrl 来源地址）
     */
    public static List<ImageSearchResult> searchByImage(String imageUrl) {
        String searchUrl = BING_SEARCH_URL
                + "?view=detailv2&iss=sbi&form=SBIIRP"
                + "&q=imgurl:" + URLUtil.encode(imageUrl);

        try {
            Document doc = Jsoup.connect(searchUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .timeout(15000)
                    .get();

            List<ImageSearchResult> results = new ArrayList<>();

            // 解析视觉相似图片区域
            Elements imgCards = doc.select(".imgpt .iusc, .img_cont .iusc");
            if (imgCards.isEmpty()) {
                // 尝试其他选择器
                imgCards = doc.select("a.iusc");
            }

            for (Element card : imgCards) {
                ImageSearchResult result = parseCard(card);
                if (result != null) {
                    results.add(result);
                }
            }

            // 如果上面的选择器没找到，尝试直接找 mimg 图片
            if (results.isEmpty()) {
                Elements imgElements = doc.select("img.mimg");
                for (Element img : imgElements) {
                    String thumbUrl = img.attr("src");
                    if (StrUtil.isBlank(thumbUrl)) {
                        thumbUrl = img.attr("data-src");
                    }
                    if (StrUtil.isNotBlank(thumbUrl)) {
                        ImageSearchResult r = new ImageSearchResult();
                        r.setThumbUrl(thumbUrl);
                        // 尝试从父级 a 标签获取来源 URL
                        Element parent = img.parent();
                        if (parent != null && "a".equals(parent.tagName())) {
                            String href = parent.attr("href");
                            if (StrUtil.isNotBlank(href)) {
                                r.setFromUrl(href);
                            }
                        }
                        results.add(r);
                    }
                }
            }

            log.info("Bing 以图搜图完成，找到 {} 个结果", results.size());
            return results;
        } catch (Exception e) {
            log.error("Bing 以图搜图失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Bing 以图搜图失败");
        }
    }

    /**
     * 从图片卡片元素中提取缩略图和来源 URL
     *
     * @param card a.iusc 元素
     * @return ImageSearchResult
     */
    private static ImageSearchResult parseCard(Element card) {
        try {
            // 1. 从 m 属性 JSON 中提取 murl（原图直链）
            String mAttr = card.attr("m");
            if (StrUtil.isBlank(mAttr)) return null;

            JSONObject mJson = JSONUtil.parseObj(mAttr);
            String murl = mJson.getStr("murl");
            String turl = mJson.getStr("turl");  // 缩略图

            if (StrUtil.isBlank(turl) && StrUtil.isBlank(murl)) return null;

            // 校验 URL 协议，防止 javascript: 等危险协议
            String thumbUrl = StrUtil.isNotBlank(turl) ? turl : murl;
            if (!isSafeUrl(thumbUrl) && !isSafeUrl(murl)) return null;

            ImageSearchResult result = new ImageSearchResult();
            result.setThumbUrl(isSafeUrl(thumbUrl) ? thumbUrl : null);
            result.setFromUrl(isSafeUrl(murl) ? murl : null);
            return result;
        } catch (Exception e) {
            log.debug("解析 Bing 图片卡片失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 校验 URL 是否安全（仅允许 http/https 协议）
     */
    private static boolean isSafeUrl(String url) {
        if (StrUtil.isBlank(url)) return false;
        String lower = url.toLowerCase().trim();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }
}
