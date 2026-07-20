package com.li.lipicturecloud.AI.agent;


import cn.hutool.core.util.StrUtil;
import com.li.lipicturecloud.AI.agent.model.AgentState;
import com.li.lipicturecloud.AI.common.UserContextHolder;
import com.li.lipicturecloud.model.entity.User;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 *  抽象基础代理类，用于管理代理状态和执行流程。
 *  提供状态转换、内存管理和基于步骤的执行循环的基础功能。
 *  子类必须实现step方法。
 */
@Data
@Slf4j
public abstract class BaseAgent {

    // 代理的名称
    private String name;
    // 代理的系统提示词
    private String systemPrompt;
    // 代理的下一步提示词
    private String nextStepPrompt;
    // 代理的状态
    private AgentState state = AgentState.IDLE;
    //当前执行步骤数
    private int currentStep = 0;
    // 代理的最大执行步骤数
    private int maxSteps = 10;
    // 代理的ChatClient实例
    private ChatClient chatClient;
    // 自主维护会话上下文
    private List<Message> messageList = new ArrayList<>();
    /**
     * 运行代理
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public String run(String userPrompt) {
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }
        // 更改状态
        state = AgentState.RUNNING;
        // 记录消息上下文
        messageList.add(new UserMessage(userPrompt));
        // 保存结果列表
        List<String> results = new ArrayList<>();
        try {
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step " + stepNumber + "/" + maxSteps);
                // 单步执行
                String stepResult = step();
                String result = "Step " + stepNumber + ": " + stepResult;
                results.add(result);
            }
            // 检查是否超出步骤限制
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("Error executing agent", e);
            return "执行错误" + e.getMessage();
        } finally {
            // 清理资源
            this.cleanup();
        }
    }

    /**
     * 流式运行代理
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public SseEmitter runStream(String userPrompt) {
        return runStream(userPrompt, null);
    }

    /**
     * 流式运行代理（带用户上下文）
     * <p>
     * 在异步工作线程内设置 UserContextHolder，确保 AI 工具能获取当前用户。
     * 执行完毕或出错时自动清理，防止 ThreadLocal 泄漏。
     *
     * @param userPrompt 用户输入
     * @param currentUser 当前用户（可 null）
     */
    public SseEmitter runStream(String userPrompt, User currentUser) {
        this.state = AgentState.IDLE;
        this.currentStep = 0;

        SseEmitter emitter = new SseEmitter(300000L);

        CompletableFuture.runAsync(()->{
            UserContextHolder.set(currentUser);
            try {
                if (StrUtil.isBlank(userPrompt)) {
                    emitter.send("Agent 没有收到消息");
                    emitter.complete();
                    return;
                }

                state = AgentState.RUNNING;
                // 多轮记忆：保留历史消息，追加新用户消息
                messageList.add(new UserMessage(userPrompt));
                List<String> results = new ArrayList<>();
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    log.info("Executing step " + stepNumber + "/" + maxSteps);
                    String stepResult = step();
                    boolean isFinal = state == AgentState.FINISHED;

                    if (isFinal && stepResult != null) {
                        emitter.send(stepResult);
                    } else if (stepResult != null && !stepResult.isBlank()) {
                        // 工具执行日志折叠在 HTML 注释中，Markdown 渲染时自动隐藏
                        emitter.send("<!-- " + stepResult.replace("\n", " ") + " -->");
                    }
                }
                // 检查是否超出步骤限制
                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    results.add("Terminated: Reached max steps (" + maxSteps + ")");
                    emitter.send("Terminated: Reached max steps (" + maxSteps + ")");
                }
                emitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("Error executing agent", e);
                try {
                    emitter.send("执行错误" + e.getMessage());
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            } finally {
                this.cleanup();
                UserContextHolder.clear();  // ★ 防止 ThreadLocal 泄漏
            }
            emitter.onTimeout(()->{
                this.state = AgentState.ERROR;
                this.cleanup();
                UserContextHolder.clear();
                log.warn("Agent execution timed out");
            });
            emitter.onCompletion(()->{
                if(this.state==AgentState.RUNNING){
                    this.state = AgentState.FINISHED;
                }
                this.cleanup();
                UserContextHolder.clear();
                log.info("Agent execution completed");
            });
        });
        return emitter;
    }
    /**
     * 执行单个步骤
     *
     * @return 步骤执行结果
     */
    public abstract String step();

    /**
     * 清理资源
     */
    protected void cleanup() {
        // 子类可以重写此方法来清理资源
    }


}
