package com.li.lipicturecloud.domain.airuntime;

/**
 * 已识别的模型供应商。供应商名称不是兼容性承诺，能力以连接测试快照为准。
 */
public enum ModelProvider {
    OPENAI,
    ANTHROPIC,
    GOOGLE,
    DEEPSEEK,
    MOONSHOT,
    DASHSCOPE
}
