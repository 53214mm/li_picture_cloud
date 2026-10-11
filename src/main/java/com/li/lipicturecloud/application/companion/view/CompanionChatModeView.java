package com.li.lipicturecloud.application.companion.view;

/**
 * 伙伴聊天模式的展示视图。
 *
 * @param mode           实际生效的模式（DEMO / MODEL）
 * @param modelAvailable 当前能否切到 MODEL
 * @param modelSource    MODEL 使用的模型来源：OWN（自己的模型）、PLATFORM（平台模型）、NONE（不可用）
 * @param modelBroken    自己的模型连接已失效（MODEL 会报错，直到重新连接或改用平台）
 * @param providerLabel  自己的模型所属厂商，来源不是 OWN 时为 null
 * @param modelCode      自己的模型编码，来源不是 OWN 时为 null
 */
public record CompanionChatModeView(String mode, boolean modelAvailable, String modelSource,
                                    boolean modelBroken, String providerLabel, String modelCode) {
}
