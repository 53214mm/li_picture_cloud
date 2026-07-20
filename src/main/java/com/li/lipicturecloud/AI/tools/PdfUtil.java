package com.li.lipicturecloud.AI.tools;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.utils.PageRange;
import com.itextpdf.kernel.utils.PdfMerger;
import com.itextpdf.kernel.utils.PdfSplitter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 工具类 —— 基于 iText 9
 * <p>
 * 提供 PDF 的创建、读取、合并、拆分等核心操作。
 * 内置中文字体支持（依赖 font-asian）。
 */
public class PdfUtil {

    private static final Logger log = LoggerFactory.getLogger(PdfUtil.class);

    /**
     * 创建 PDF 文件，写入纯文本内容（支持中文）。
     *
     * @param filePath 输出路径，如 "tmp/file/report.pdf"
     * @param content  文本内容
     * @return 生成的文件路径
     */
    public static String createPdf(String filePath, String content) {
        ensureDir(filePath);
        try (PdfWriter writer = new PdfWriter(filePath);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc)) {

            PdfFont cjkFont = loadCjkFont();

            String[] paragraphs = content.split("\\n");
            for (String line : paragraphs) {
                if (line.isBlank()) {
                    doc.add(new Paragraph(" ").setFont(cjkFont));
                } else {
                    doc.add(new Paragraph(line).setFont(cjkFont));
                }
            }

            log.info("PDF 创建成功: {}", filePath);
            return filePath;

        } catch (Exception e) {
            log.error("创建 PDF 失败: {}", e.getMessage(), e);
            throw new RuntimeException("创建 PDF 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 PDF 中提取全部文本内容。
     */
    public static String extractText(String filePath) {
        StringBuilder sb = new StringBuilder();
        try (PdfReader reader = new PdfReader(filePath);
             PdfDocument pdfDoc = new PdfDocument(reader)) {

            int totalPages = pdfDoc.getNumberOfPages();
            for (int i = 1; i <= totalPages; i++) {
                String pageText = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
                        .getTextFromPage(pdfDoc.getPage(i));
                sb.append(pageText);
                if (i < totalPages) {
                    sb.append("\n");
                }
            }

            log.info("PDF 文本提取成功: {}，共 {} 页", filePath, totalPages);
            return sb.toString();

        } catch (Exception e) {
            log.error("读取 PDF 失败: {}", e.getMessage(), e);
            throw new RuntimeException("读取 PDF 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按页提取文本，返回每页内容的列表。
     */
    public static List<String> extractTextByPage(String filePath) {
        List<String> pages = new ArrayList<>();
        try (PdfReader reader = new PdfReader(filePath);
             PdfDocument pdfDoc = new PdfDocument(reader)) {

            int totalPages = pdfDoc.getNumberOfPages();
            for (int i = 1; i <= totalPages; i++) {
                String pageText = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
                        .getTextFromPage(pdfDoc.getPage(i));
                pages.add(pageText);
            }

            log.info("PDF 分页提取成功: {}，共 {} 页", filePath, totalPages);
            return pages;

        } catch (Exception e) {
            log.error("分页读取 PDF 失败: {}", e.getMessage(), e);
            throw new RuntimeException("分页读取 PDF 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取 PDF 总页数。
     */
    public static int getPageCount(String filePath) {
        try (PdfReader reader = new PdfReader(filePath);
             PdfDocument pdfDoc = new PdfDocument(reader)) {
            return pdfDoc.getNumberOfPages();
        } catch (Exception e) {
            log.error("获取 PDF 页数失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取 PDF 页数失败: " + e.getMessage(), e);
        }
    }

    /**
     * 合并多个 PDF 文件为一个。
     * <p>
     * iText 9: PdfMerger.merge(PdfDocument, List&lt;Integer&gt;)
     */
    public static String mergePdfs(List<String> inputPaths, String outputPath) {
        if (inputPaths == null || inputPaths.isEmpty()) {
            throw new IllegalArgumentException("输入 PDF 文件列表不能为空");
        }
        ensureDir(outputPath);

        try (PdfWriter writer = new PdfWriter(outputPath);
             PdfDocument outputDoc = new PdfDocument(writer)) {

            PdfMerger merger = new PdfMerger(outputDoc);

            for (String inputPath : inputPaths) {
                try (PdfReader reader = new PdfReader(inputPath);
                     PdfDocument inputDoc = new PdfDocument(reader)) {
                    List<Integer> allPages = new ArrayList<>();
                    int n = inputDoc.getNumberOfPages();
                    for (int p = 1; p <= n; p++) {
                        allPages.add(p);
                    }
                    merger.merge(inputDoc, allPages);
                }
            }

            merger.close();
            log.info("PDF 合并成功: {} 个文件 → {}", inputPaths.size(), outputPath);
            return outputPath;

        } catch (Exception e) {
            log.error("合并 PDF 失败: {}", e.getMessage(), e);
            throw new RuntimeException("合并 PDF 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 拆分 PDF：将每一页保存为独立文件。
     * <p>
     * iText 9: PdfSplitter.getNextPdfWriter(PageRange) 中 PageRange 是
     * com.itextpdf.kernel.utils.PageRange。
     */
    public static List<String> splitPdf(String inputPath, String outputDir) {
        File dir = new File(outputDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("无法创建输出目录: " + outputDir);
        }

        List<String> resultPaths = new ArrayList<>();
        String baseName = new File(inputPath).getName().replace(".pdf", "");

        try (PdfReader reader = new PdfReader(inputPath);
             PdfDocument pdfDoc = new PdfDocument(reader)) {

            PdfSplitter splitter = new PdfSplitter(pdfDoc) {
                int pageNum = 0;

                @Override
                protected PdfWriter getNextPdfWriter(PageRange documentPageRange) {
                    pageNum++;
                    String outPath = outputDir + "/" + baseName + "_page_" + pageNum + ".pdf";
                    resultPaths.add(outPath);
                    try {
                        return new PdfWriter(outPath);
                    } catch (IOException e) {
                        throw new RuntimeException("拆分 PDF 写入失败: " + outPath, e);
                    }
                }
            };

            splitter.splitByPageCount(1);
            log.info("PDF 拆分成功: {} → {} 个文件", inputPath, resultPaths.size());
            return resultPaths;

        } catch (Exception e) {
            log.error("拆分 PDF 失败: {}", e.getMessage(), e);
            throw new RuntimeException("拆分 PDF 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 为已有 PDF 添加文本水印。
     */
    public static String addWatermark(String inputPath, String outputPath,
                                       String watermark, float fontSize, float opacity) {
        ensureDir(outputPath);

        try (PdfReader reader = new PdfReader(inputPath);
             PdfWriter writer = new PdfWriter(outputPath);
             PdfDocument pdfDoc = new PdfDocument(reader, writer)) {

            PdfFont cjkFont = loadCjkFont();
            int totalPages = pdfDoc.getNumberOfPages();

            for (int i = 1; i <= totalPages; i++) {
                var page = pdfDoc.getPage(i);
                var pageSize = page.getPageSize();
                float x = pageSize.getWidth() / 2;
                float y = pageSize.getHeight() / 2;

                var canvas = new com.itextpdf.kernel.pdf.canvas.PdfCanvas(page);
                canvas.saveState();
                canvas.setExtGState(new com.itextpdf.kernel.pdf.extgstate.PdfExtGState()
                        .setFillOpacity(opacity));

                canvas.beginText()
                        .setFontAndSize(cjkFont, fontSize)
                        .moveText(x - watermark.length() * fontSize / 4f, y)
                        .showText(watermark)
                        .endText();

                canvas.restoreState();
                canvas.release();
            }

            log.info("PDF 水印添加成功: {}", outputPath);
            return outputPath;

        } catch (Exception e) {
            log.error("添加水印失败: {}", e.getMessage(), e);
            throw new RuntimeException("添加水印失败: " + e.getMessage(), e);
        }
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 加载中文字体，优先使用 font-asian 内置字体，失败时回退系统字体。
     */
    @SuppressWarnings("SpellCheckingInspection")
    private static PdfFont loadCjkFont() throws IOException {
        try {
            return PdfFontFactory.createFont(
                    "com/itextpdf/font/STSong-Light.ttf",
                    "Identity-H",
                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
        } catch (Exception e) {
            log.warn("无法加载内置中文字体，尝试系统字体: {}", e.getMessage());
            try {
                // Windows 系统回退字体
                return PdfFontFactory.createFont("C:/Windows/Fonts/simsun.ttc,0",
                        "Identity-H", PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
            } catch (Exception ex) {
                log.warn("无法加载系统中文字体，使用 Helvetica");
                return PdfFontFactory.createFont();
            }
        }
    }

    /**
     * 确保输出目录存在。
     */
    private static void ensureDir(String filePath) {
        File parent = new File(filePath).getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new RuntimeException("无法创建输出目录: " + parent.getAbsolutePath());
        }
    }
}
