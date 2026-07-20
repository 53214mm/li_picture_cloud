package com.li.lipicturecloud.AI.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * PDF 生成与处理工具 —— 供 Spring AI 智能体调用
 * <p>
 * 底层委托给 {@link PdfUtil}，封装为 @Tool 方法以自动注册到工具链。
 */
public class PDFGenerationTool {

    @Tool(description = "创建 PDF 文件，将指定文本内容写入 PDF（支持中文）")
    public String createPdf(
            @ToolParam(description = "输出文件路径，如 tmp/file/report.pdf") String filePath,
            @ToolParam(description = "要写入 PDF 的文本内容") String content) {
        return PdfUtil.createPdf(filePath, content);
    }

    @Tool(description = "从 PDF 文件中提取全部文本内容")
    public String extractText(
            @ToolParam(description = "PDF 文件路径") String filePath) {
        return PdfUtil.extractText(filePath);
    }

    @Tool(description = "获取 PDF 文件的总页数")
    public int getPageCount(
            @ToolParam(description = "PDF 文件路径") String filePath) {
        return PdfUtil.getPageCount(filePath);
    }

    @Tool(description = "合并多个 PDF 文件为一个。inputPaths 是用逗号分隔的输入文件路径列表，outputPath 是合并后的输出路径")
    public String mergePdfs(
            @ToolParam(description = "要合并的 PDF 文件路径，用逗号分隔") String inputPathsStr,
            @ToolParam(description = "合并后输出的文件路径") String outputPath) {
        List<String> inputPaths = List.of(inputPathsStr.split(","));
        return PdfUtil.mergePdfs(inputPaths, outputPath);
    }

    @Tool(description = "拆分 PDF，将每一页保存为独立文件到指定目录")
    public String splitPdf(
            @ToolParam(description = "要拆分的 PDF 文件路径") String inputPath,
            @ToolParam(description = "拆分后的输出目录") String outputDir) {
        List<String> result = PdfUtil.splitPdf(inputPath, outputDir);
        return "拆分成功，共 " + result.size() + " 个文件，输出目录: " + outputDir;
    }
}
