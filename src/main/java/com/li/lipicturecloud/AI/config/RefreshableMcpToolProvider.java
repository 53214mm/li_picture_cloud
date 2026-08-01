package com.li.lipicturecloud.AI.config;

import cn.hutool.core.util.StrUtil;
import com.li.lipicturecloud.AI.common.UserContextHolder;
import com.li.lipicturecloud.AI.service.McpGeneratedImageHandler;
import com.li.lipicturecloud.model.entity.User;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 可刷新的 MCP 工具提供者
 * <p>
 * 每次 getToolCallbacks() 只返回工具定义（不绑定 Session 到 Client）。
 * 真正调用工具时每次新建 MCP 连接。
 * <p>
 * 对异步生成类工具（generate_image / generate_video），ToolCallback 内部自动
 * 等待完成再返回结果，避免 LLM 在工具调用循环中不停轮询 get_task_status。
 */
@Slf4j
@Component
public class RefreshableMcpToolProvider implements ToolCallbackProvider {

    @Value("${mxai.api-key}")
    private String apiKey;

    @Value("${spring.ai.mcp.client.sse.connections.mxai-mcp-server.url}")
    private String mcpUrl;

    @Value("${app.mcp.enabled:true}")
    private boolean enabled;

    @Resource
    private McpSyncHttpClientRequestCustomizer mcpAuthCustomizer;
    @Resource
    private McpGeneratedImageHandler generatedImageHandler;

    /** 缓存 MCP 工具回调列表，避免每次请求都连接 MCP 列举工具 */
    private volatile ToolCallback[] cachedCallbacks = new ToolCallback[0];
    private volatile long lastRefreshTime = 0;
    private static final long CACHE_TTL_MS = 10 * 60 * 1000; // 10 分钟刷新一次

    /** taskId 提取正则会依次尝试匹配 */
    private static final Pattern[] TASK_ID_PATTERNS = {
            // 中文格式: "任务序列号: 2079161218412580864" 或 "任务ID: xxx"
            Pattern.compile("任务(?:序列号|ID|编号)[：:]\\s*(\\d+)"),
            // 英文格式: "taskId":"xxx" 或 "task_id":"xxx"
            Pattern.compile("\"(?:taskId|task_id|id)\"\\s*:\\s*\"([^\"]+)\""),
            // 纯数字 ID (19位雪花ID)
            Pattern.compile("\\b(\\d{15,20})\\b")
    };

    // ======================== 轮询相关常量 ========================

    /** 异步生成类 MCP 工具：调用后在 Java 层等待结果，不从 LLM 层轮询 */
    private static final Set<String> GENERATION_TOOLS = Set.of("generate_image", "generate_video");

    /** 初次等待时长（图片生成至少 1 分钟以上，不浪费轮询） */
    private static final long INITIAL_WAIT_MS = 50_000;

    /** 轮询间隔（指数退避）：2s → 4s → 8s → 16s → 30s，之后每 30s 一次 */
    private static final long[] POLL_BACKOFF_MS = {2000, 4000, 8000, 16000, 30000};

    /** 最大总等待时间（超过则返回 taskId 让用户稍后手动查询） */
    private static final long MAX_POLL_TOTAL_MS = 240_000;

    // ======================== 工具列表获取 ========================

    @Override
    public ToolCallback[] getToolCallbacks() {
        if (!enabled) {
            return new ToolCallback[0];
        }
        if (System.currentTimeMillis() - lastRefreshTime < CACHE_TTL_MS
                && cachedCallbacks.length > 0) {
            return cachedCallbacks;
        }
        synchronized (this) {
            if (System.currentTimeMillis() - lastRefreshTime < CACHE_TTL_MS
                    && cachedCallbacks.length > 0) {
                return cachedCallbacks;
            }
            List<ToolCallback> callbacks = new ArrayList<>();
            McpSyncClient client = null;
            try {
                var transportBuilder = new HttpClientSseClientTransport.Builder(mcpUrl)
                        .connectTimeout(java.time.Duration.ofSeconds(30));
                if (mcpAuthCustomizer != null) {
                    transportBuilder.httpRequestCustomizer(mcpAuthCustomizer);
                }
                client = McpClient.sync(transportBuilder.build()).build();
                client.initialize();
                McpSchema.ListToolsResult tools = client.listTools();

                for (McpSchema.Tool tool : tools.tools()) {
                    String name = tool.name();
                    String desc = tool.description() != null ? tool.description() : "";
                    // ★ 从 MCP 服务端获取真实的 JSON Schema
                    String inputSchema = "{}";
                    if (tool.inputSchema() != null) {
                        try {
                            inputSchema = new com.fasterxml.jackson.databind.ObjectMapper()
                                    .writeValueAsString(tool.inputSchema());
                        } catch (Exception e) {
                            log.warn("MCP 工具 {} 的 inputSchema 序列化失败，使用空 schema", name, e);
                        }
                    }
                    callbacks.add(new FreshSessionMcpCallback(name, desc, inputSchema));
                }
                cachedCallbacks = callbacks.toArray(new ToolCallback[0]);
                lastRefreshTime = System.currentTimeMillis();
                log.info("MCP 工具列表刷新：{} 个，工具名: {}",
                        callbacks.size(),
                        callbacks.stream().map(ToolCallback::getToolDefinition)
                                .map(ToolDefinition::name).toList());
            } catch (Exception e) {
                log.warn("MCP 工具列表获取失败: {}", e.getMessage());
            } finally {
                if (client != null) { try { client.close(); } catch (Exception ignored) {} }
            }
            return cachedCallbacks;
        }
    }

    // ======================== ToolCallback 实现 ========================

    /**
     * 每次调用都新建 MCP 连接的自定义 ToolCallback。
     * <p>
     * 对生成类工具（generate_image / generate_video），调用后自动在同一连接上
     * 等待任务完成，避免 LLM 在工具调用循环中反复调用 get_task_status。
     */
    private class FreshSessionMcpCallback implements ToolCallback {

        private final String toolName;
        private final String toolDescription;
        private final String inputSchema;

        FreshSessionMcpCallback(String name, String desc, String schema) {
            this.toolName = name;
            this.toolDescription = desc;
            this.inputSchema = schema;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder()
                    .name(toolName)
                    .description(toolDescription)
                    .inputSchema(inputSchema)
                    .build();
        }

        @Override
        public String call(String toolInput) {
            // ★ 在 call() 入口捕获 User（避免依赖 ThreadLocal 在 reactor 线程中为 null）
            User currentUser = UserContextHolder.get();
            log.info(">>> MCP ToolCallback.call() 被调用 | toolName={} | isGeneration={} | user={}",
                    toolName, GENERATION_TOOLS.contains(toolName),
                    currentUser != null ? currentUser.getId() : "null");
            McpSyncClient client = null;
            try {
                // ★ 每次调用新建连接，Session 永不过期
                var transportBuilder = new HttpClientSseClientTransport.Builder(mcpUrl);
                if (mcpAuthCustomizer != null) {
                    transportBuilder.httpRequestCustomizer(mcpAuthCustomizer);
                }
                client = McpClient.sync(transportBuilder.build()).build();
                client.initialize();

                @SuppressWarnings("unchecked")
                var args = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(toolInput, Map.class);
                McpSchema.CallToolResult result = client.callTool(
                        new McpSchema.CallToolRequest(toolName, args));
                String text = extractText(result);

                // ★ 对生成类工具：Java 层等待完成（复用同一 MCP 连接轮询）
                if (GENERATION_TOOLS.contains(toolName)) {
                    log.info("MCP 生成工具 {} 已调用，开始等待完成...", toolName);
                    text = pollUntilComplete(text);
                    // 完成后自动保存到用户空间
                    text = generatedImageHandler.appendSaveResult(text, currentUser);
                    return text;
                }

                // get_task_status 返回图片 URL 时，自动保存到用户空间
                if ("get_task_status".equals(toolName)) {
                    text = generatedImageHandler.appendSaveResult(text, currentUser);
                }
                return text;
            } catch (Exception e) {
                log.warn("MCP 工具 {} 调用失败: {}", toolName, e.getMessage());
                return "调用失败，请稍后重试。";
            } finally {
                if (client != null) { try { client.close(); } catch (Exception ignored) {} }
            }
        }
    }

    // ======================== 轮询逻辑 ========================

    /**
     * 轮询 get_task_status，等待生成任务完成。
     * <p>
     * 策略：先等 50s（图片生成至少 1 分钟），再指数退避轮询（2s→4s→8s→16s→30s...），
     * 最大总等待 240s。
     * <p>
     * ★ 每次轮询新建独立 MCP 连接，不尝试复用（MCP SSE 有 30s 空闲超时）。
     *
     * @param generationResult 生成工具的初始返回文本
     * @return 最终结果（含图片 URL）或超时提示
     */
    private String pollUntilComplete(String generationResult) {
        log.info(">>> generate_image 原始返回: {}", generationResult);

        // 1. 提取 taskId
        String taskId = extractTaskId(generationResult);
        if (taskId == null) {
            log.info("生成工具同步返回结果（无 taskId），直接返回");
            return stripPollingInstruction(generationResult);
        }

        log.info("开始等待 MCP 任务 {} 完成（先等 {}s，期间不保持连接）...",
                taskId, INITIAL_WAIT_MS / 1000);

        // 2. 先等足够长时间（图片生成至少 1 分钟，不保持 MCP 连接）
        try {
            Thread.sleep(INITIAL_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return buildTimeoutResult(taskId);
        }

        // 3. 指数退避轮询（每次新建连接）
        long startTime = System.currentTimeMillis();
        int pollCount = 0;

        while (System.currentTimeMillis() - startTime < MAX_POLL_TOTAL_MS) {
            long waitMs;
            if (pollCount < POLL_BACKOFF_MS.length) {
                waitMs = POLL_BACKOFF_MS[pollCount];
            } else {
                waitMs = POLL_BACKOFF_MS[POLL_BACKOFF_MS.length - 1];
            }

            if (pollCount > 0) {
                try { Thread.sleep(waitMs); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return buildTimeoutResult(taskId);
                }
            }

            pollCount++;
            String statusText = callMcpGetTaskStatus(taskId);
            long elapsed = System.currentTimeMillis() - startTime + INITIAL_WAIT_MS;
            log.info("MCP 任务 {} 第 {} 次轮询（已等 {}s）: {}",
                    taskId, pollCount, elapsed / 1000,
                    statusText != null ? statusText.substring(0, Math.min(100, statusText.length())) : "null");

            if (statusText == null) continue;

            if (statusText.contains("http")) {
                log.info("MCP 任务 {} 完成！总等待 {}s，轮询 {} 次", taskId, elapsed / 1000, pollCount);
                return stripPollingInstruction(statusText);
            }

            String lower = statusText.toLowerCase();
            if (lower.contains("fail") || lower.contains("error")
                    || statusText.contains("失败") || statusText.contains("错误")) {
                log.warn("MCP 任务 {} 失败: {}", taskId, statusText);
                return "图片生成失败: " + statusText;
            }
        }

        log.warn("MCP 任务 {} 超时（{}s），返回 taskId 供后续查询", taskId, MAX_POLL_TOTAL_MS / 1000);
        return buildTimeoutResult(taskId);
    }

    /**
     * 每次新建独立 MCP 连接调用 get_task_status。
     * <p>
     * 不复用连接——MCP SSE 有空闲超时(~30s)，长时间等待后旧连接已不可用。
     */
    private String callMcpGetTaskStatus(String taskId) {
        McpSyncClient client = null;
        try {
            var transportBuilder = new HttpClientSseClientTransport.Builder(mcpUrl)
                    .connectTimeout(java.time.Duration.ofSeconds(30));
            if (mcpAuthCustomizer != null) {
                transportBuilder.httpRequestCustomizer(mcpAuthCustomizer);
            }
            client = McpClient.sync(transportBuilder.build()).build();
            client.initialize();

            @SuppressWarnings("unchecked")
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("get_task_status",
                            Map.of("serial_no", taskId)));
            return extractText(result);
        } catch (Exception e) {
            log.warn("callMcpGetTaskStatus 失败 (taskId={}): {}", taskId, e.getMessage());
            return null;
        } finally {
            if (client != null) { try { client.close(); } catch (Exception ignored) {} }
        }
    }

    // ======================== 辅助方法 ========================

    /** 从 MCP 调用结果文本中提取 taskId，依次尝试中文格式 → JSON格式 → 纯数字 */
    private String extractTaskId(String text) {
        if (text == null) return null;
        for (Pattern p : TASK_ID_PATTERNS) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                log.info("taskId 提取成功: {} (正则: {})", m.group(1), p.pattern());
                return m.group(1);
            }
        }
        return null;
    }

    /** 从 CallToolResult 提取文本内容 */
    private String extractText(McpSchema.CallToolResult result) {
        if (result == null || result.content() == null) return null;
        return result.content().stream()
                .filter(c -> c instanceof McpSchema.TextContent)
                .map(c -> ((McpSchema.TextContent) c).text())
                .reduce("", (a, b) -> a + b);
    }

    /** 过滤 MCP 返回中的轮询诱导文本，防止 LLM 继续轮询 */
    private String stripPollingInstruction(String text) {
        if (text == null) return null;
        return text
                .replaceAll("请自动调用 get_task_status[^\n]*[\n]?", "")
                .replaceAll("请继续调用.*?(?:get_task_status|查询).*?[\n]?", "")
                .trim();
    }

    /** 构建超时结果（含 taskId 供后续手动查询） */
    private String buildTimeoutResult(String taskId) {
        return String.format(
                "图片正在生成中（已等待 %d 秒），任务 ID: %s\n你可以稍后使用 get_task_status 并传入此 taskId 查询结果。",
                MAX_POLL_TOTAL_MS / 1000, taskId);
    }

}
