package com.li.lipicturecloud.AI.tools;

import cn.hutool.core.io.FileUtil;
import com.li.lipicturecloud.AI.common.UrlValidator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 文件操作工具类（含路径遍历防护）
 */
public class FileOperationTool {

    private final String FILE_DIR = System.getProperty("user.dir") + "/ai-files";

    @Tool(description = "读取文件内容")
    public String readFile(@ToolParam(description = "要读取的文件名") String fileName) {
        // ★ 路径遍历防护
        try {
            UrlValidator.validateFileName(fileName);
        } catch (IllegalArgumentException e) {
            return "读取文件失败: " + e.getMessage();
        }

        String filePath = FILE_DIR + "/" + fileName;
        try {
            return FileUtil.readUtf8String(filePath);
        } catch (Exception e) {
            return "读取文件失败: " + e.getMessage();
        }
    }

    @Tool(description = "写入文件内容")
    public String writeFile(@ToolParam(description = "要写入的文件名") String fileName,
                            @ToolParam(description = "要写入的内容") String content) {
        // ★ 路径遍历防护
        try {
            UrlValidator.validateFileName(fileName);
        } catch (IllegalArgumentException e) {
            return "写入文件失败: " + e.getMessage();
        }

        String filePath = FILE_DIR + "/" + fileName;
        FileUtil.touch(filePath);
        try {
            FileUtil.writeUtf8String(content, filePath);
            return "文件写入成功: " + filePath;
        } catch (Exception e) {
            return "写入文件失败: " + e.getMessage();
        }
    }
}
