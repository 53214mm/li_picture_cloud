package com.li.lipicturecloud.AI.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 全网智能搜索工具，基于千帆 AI 搜索引擎进行联网检索
 */
@Component
public class SearchTool {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.ai.qianfan.Bearer:}")
    private String bearer;

    public static final OkHttpClient HTTP_CLIENT = new OkHttpClient().newBuilder()
            .readTimeout(300, TimeUnit.SECONDS).build();

    @Tool(description = "全网智能搜索工具，根据用户问题联网检索最新网络资讯")
    public String search(@ToolParam(description = "用户需要上网搜索查询的具体问题") String query) throws IOException {
        if (bearer == null || bearer.isBlank()) {
            return "搜索工具未配置 API Key，请联系管理员。";
        }

        // ★ 使用 ObjectMapper 安全构造 JSON，防止 JSON 注入
        Map<String, Object> requestBody = Map.of(
                "instruction", "请根据联网搜索结果，客观总结回答用户问题，引用搜索来源，简洁准确作答",
                "messages", List.of(
                        Map.of("role", "user", "content", query)
                ),
                "resource_type_filter", List.of(
                        Map.of("type", "web", "top_k", 20),
                        Map.of("type", "video", "top_k", 1),
                        Map.of("type", "image", "top_k", 1)
                ),
                "search_filter", Map.of(
                        "match", Map.of("site", List.of("tieba.baidu.com", "baike.baidu.com")),
                        "range", Map.of("page_time", Map.of("gt", "now-1w/d"))
                )
        );

        String requestJson = objectMapper.writeValueAsString(requestBody);
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(requestJson, mediaType);
        Request request = new Request.Builder()
                .url("https://qianfan.baidubce.com/v2/ai_search/web_summary")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", bearer)
                .build();
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (response.body() != null) {
                return response.body().string();
            } else {
                return "搜索接口返回空";
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "搜索异常：" + e.getMessage();
        }
    }
}
