package com.li.lipicturecloud.AI.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.li.lipicturecloud.AI.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于工具调用的智能体实现，封装工具选择与执行逻辑
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    private ToolCallback[] availableTools;
    private ToolCallbackProvider mcpToolProvider;  // 每次 think() 时动态刷新，保持 Session 活跃
    private ChatResponse toolCallChatResponse;
    private final ToolCallingManager toolCallingManager;
    private final ChatOptions chatOptions;
    private String lastThinkText;

    private static final String NL = "\n";

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }

    @Override
    public boolean think() {
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            getMessageList().add(new UserMessage(getNextStepPrompt()));
        }
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        try {
            // 本地工具 + MCP Provider（由 Spring AI 内部管理 Session，避免过期）
            var promptCall = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools);
            if (mcpToolProvider != null) {
                promptCall = promptCall.toolCallbacks(mcpToolProvider);
            }

            ChatResponse chatResponse = promptCall.call().chatResponse();
            this.toolCallChatResponse = chatResponse;
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            String result = assistantMessage.getText();
            this.lastThinkText = result;
            log.info("agent_think_completed agent={} toolCallCount={} toolNames={}",
                    getName(), toolCallList.size(),
                    toolCallList.stream().map(AssistantMessage.ToolCall::name).toList());
            if (toolCallList.isEmpty()) {
                getMessageList().add(assistantMessage);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("agent_think_failed agent={} exceptionType={}",
                    getName(), e.getClass().getName());
            getMessageList().add(new AssistantMessage("处理时遇到了错误，请稍后重试。"));
            return false;
        }
    }

    @Override
    public String step() {
        try {
            boolean shouldAct = think();
            if (!shouldAct) {
                setState(AgentState.FINISHED);
                return lastThinkText != null ? lastThinkText : "思考完成 - 无需行动";
            }
            String thinkText = lastThinkText;
            String actionResult = act();
            if (thinkText != null && !thinkText.isBlank()) {
                return "THINK: " + thinkText + NL + "ACT: " + actionResult;
            }
            return actionResult;
        } catch (Exception e) {
            log.warn("agent_step_failed agent={} exceptionType={}",
                    getName(), e.getClass().getName());
            return "步骤执行失败，请稍后重试。";
        }
    }

    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具需要调用";
        }
        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());

        String results = toolResponseMessage.getResponses().stream()
                .map(r -> "工具 " + r.name() + " 返回的结果：" + r.responseData())
                .collect(Collectors.joining(NL));
        log.info("agent_tool_execution_completed agent={} responseCount={} toolNames={}",
                getName(), toolResponseMessage.getResponses().size(),
                toolResponseMessage.getResponses().stream().map(response -> response.name()).toList());

        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(r -> r.name().equals("doTerminate"));
        if (terminateToolCalled) {
            setState(AgentState.FINISHED);
            if (lastThinkText != null && !lastThinkText.isBlank()) {
                return lastThinkText + NL + NL + results;
            }
        }
        return results;
    }
}
