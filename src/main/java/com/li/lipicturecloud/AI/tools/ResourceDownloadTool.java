package com.li.lipicturecloud.AI.tools;


import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.li.lipicturecloud.AI.common.UrlValidator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;

/**
 * 资源下载工具类（含 SSRF + 路径遍历防护）
 */
public class ResourceDownloadTool {

    @Tool(description = "Download a resource from a given URL")
    public String downloadResource(
            @ToolParam(description = "URL of the resource to download") String url,
            @ToolParam(description = "Name of the file to save the downloaded resource") String fileName) {

        // ★ SSRF 防护：URL 白名单 + 内网 IP 过滤
        try {
            UrlValidator.validate(url);
        } catch (IllegalArgumentException e) {
            return "下载失败: " + e.getMessage();
        }

        // ★ 路径遍历防护：文件名合法性校验
        try {
            UrlValidator.validateFileName(fileName);
        } catch (IllegalArgumentException e) {
            return "下载失败: " + e.getMessage();
        }

        String fileDir = System.getProperty("user.dir") + "/ai-files/download";
        String filePath = fileDir + "/" + fileName;
        try {
            FileUtil.mkdir(fileDir);
            HttpUtil.downloadFile(url, new File(filePath));
            return "Resource downloaded successfully to: " + filePath;
        } catch (Exception e) {
            return "Error downloading resource: " + e.getMessage();
        }
    }
}
