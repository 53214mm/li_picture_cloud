package com.li.lipicturecloud.AI.app;

import com.li.lipicturecloud.AI.advisor.MyLoggerAdvisor;
import com.li.lipicturecloud.AI.chatMemory.RedisBasedChatMemory;
import com.li.lipicturecloud.AI.common.UserContextHolder;
import com.li.lipicturecloud.model.entity.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 图片云 AI 核心应用类，封装对话、RAG 检索、工具调用等核心功能
 */
@Component
@Slf4j
public class PicCloudApp {
    static {
        // ★ 先触发 UserContextHolder 加载，确保其 ContextRegistry 注册在 Hooks 初始化之前完成
        UserContextHolder.clear();
        reactor.core.publisher.Hooks.enableAutomaticContextPropagation();
    }

    private final ChatClient chatClient;
    private static final String SYSTEM_PROMPT = """
            你是 LiPictureCloud 图片云平台的 AI 助手，具备以下能力：

            ## 可用工具
            - 📷 **图片搜索**：在公开图库中按关键词搜索图片、查看图片详情、浏览最新上传
            - 📊 **图片分析**：分析图片格式、尺寸、宽高比，给出压缩和格式转换建议
            - 💾 **保存到空间**：将生成的图片保存到用户的私有空间（需先生成图片获得 URL）
            - 🎨 **AI 生图/视频**：调用 generate_image / generate_video 生成图片或视频（工具会自动等待结果，无需轮询）
            - 🔍 **以图生图**：提供参考图 URL 生成风格相似的图片
            - 📋 **查询历史任务**：通过 get_task_status 查询之前告知过 taskId 的历史异步任务

            ## 核心规则
            1. 调用工具后，**必须如实汇报工具返回的结果**，绝不能编造或猜测
            2. 工具调用失败时，如实告知用户失败原因，不要假装成功
            3. 用户只是闲聊打招呼时，直接回复即可，不需要调用工具
            4. 用中文回复，简洁友好，适当使用 Markdown 排版
            5. **重要**：当工具返回图片 URL 时，用 Markdown 图片语法展示：`![描述](图片URL)`
            6. 搜索图片时，将图片缩略图以 Markdown 格式直接展示给用户浏览
            7. 生成图片获得返回链接后，主动展示并告知用户图片已自动保存到空间
            """;

    public PicCloudApp(@Qualifier("dashScopeChatModel") ChatModel dashscopeChatModel,
                       RedisBasedChatMemory redisBasedChatMemory) {
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(redisBasedChatMemory).build(),
                        new MyLoggerAdvisor()
                )
                .build();
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
     */
    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        return chatResponse.getResult().getOutput().getText();
    }

    /**
     * AI 基础对话（流式）
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .stream()
                .content();
    }

    @Resource
    private ToolCallback[] allTools;
    @Resource
    private ToolCallbackProvider refreshableMcpToolProvider;

    /**
     * AI 流式对话（带工具调用 + MCP 工具）
     */
    public Flux<String> doChatStream(String message, String chatId, User currentUser) {
        UserContextHolder.set(currentUser);

        // 合并本地工具 + MCP 工具
        List<ToolCallback> combined = new ArrayList<>(Arrays.asList(allTools));
        ToolCallback[] mcpCallbacks = refreshableMcpToolProvider.getToolCallbacks();
        // ★ 包装 MCP 回调：在 call() 执行前注入用户上下文
        //    绕过 reactor 线程 ThreadLocal 传播问题
        for (int i = 0; i < mcpCallbacks.length; i++) {
            mcpCallbacks[i] = new UserContextToolCallback(mcpCallbacks[i], currentUser);
        }
        if (mcpCallbacks.length > 0) {
            combined.addAll(Arrays.asList(mcpCallbacks));
        }

        return chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .toolCallbacks(combined.toArray(new ToolCallback[0]))
                .stream().content()
                .doOnTerminate(UserContextHolder::clear)
                .doOnError(e -> UserContextHolder.clear());
    }

    /**
     * 包装 ToolCallback，在 call() 执行前设置 UserContextHolder。
     * 用于解决 reactor 线程池中 ThreadLocal 无法传播的问题。
     */
    private record UserContextToolCallback(ToolCallback delegate, User user) implements ToolCallback {
        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }
        @Override
        public String call(String toolInput) {
            UserContextHolder.set(user);
            try {
                return delegate.call(toolInput);
            } finally {
                UserContextHolder.clear();
            }
        }
    }

    /**
     * AI 对话（带工具调用 + 用户上下文）
     */
    public String doChatWithTools(String message, String chatId, User currentUser) {
        UserContextHolder.set(currentUser);
        try {
            ChatResponse chatResponse = chatClient
                    .prompt()
                    .user(message)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                    .toolCallbacks(allTools)
                    .call()
                    .chatResponse();
            return chatResponse.getResult().getOutput().getText();
        } finally {
            UserContextHolder.clear();
        }
    }
}
