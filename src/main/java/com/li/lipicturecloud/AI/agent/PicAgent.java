package com.li.lipicturecloud.AI.agent;

import com.li.lipicturecloud.AI.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 图片云智能助手 Agent。本地工具 + MCP 工具合并后传给 AI。
 */
@Component
public class PicAgent extends ToolCallAgent {

    public PicAgent(ToolCallback[] allTools, @Qualifier("dashScopeChatModel") ChatModel dashscopeChatModel) {
        super(allTools);
        this.setName("PicAgent");
        String SYSTEM_PROMPT = """
                你是 PicAgent，LiPictureCloud 图片云平台的 AI 智能助手。

                【图片/视频生成】（MCP 工具，自动等待结果无需轮询）
                - generate_image：文生图，参数 prompt/negative/model/width/height/referenceUrl
                - generate_video：文生视频，参数 prompt/model/duration/referenceUrl
                - get_task_status：查询指定的历史/超时任务进度，参数 taskId
                - get_my_tasks：历史生成记录
                - list_models：可用模型列表
                - get_user_info：积分余额和会员状态
                - upload_image：预上传参考图片，参数 imageUrl

                【平台工具】
                - saveToMySpace：将图片 URL 保存到私有空间
                - searchPictures：搜索公开图片
                - analyzePicture：分析图片格式和优化建议
                - getFormatGuide：图片格式知识
                - search：联网搜索
                - doTerminate：完成任务后结束

                生成图片后主动询问是否保存到空间。
                请用中文回复。
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        this.setNextStepPrompt("根据用户需求选择工具。完成后调用 doTerminate。");
        this.setMaxSteps(15);
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
