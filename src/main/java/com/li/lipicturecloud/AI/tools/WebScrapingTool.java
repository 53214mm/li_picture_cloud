package com.li.lipicturecloud.AI.tools;

import com.li.lipicturecloud.AI.common.UrlValidator;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;

/**
 * 网页爬取工具（含 SSRF 防护），用于抓取指定 URL 的网页内容
 */
public class WebScrapingTool {

    @Tool(description = "抓取网页内容")
    public String scrapeWebPage(@ToolParam(description = "要抓取的网页的URL") String url) {
        // ★ SSRF 防护
        try {
            UrlValidator.validate(url);
        } catch (IllegalArgumentException e) {
            return "抓取失败: " + e.getMessage();
        }

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0")
                    .timeout(10000)
                    .get();
            return doc.html();
        } catch (IOException e) {
            return "抓取网页时出错：" + e.getMessage();
        }
    }
}
