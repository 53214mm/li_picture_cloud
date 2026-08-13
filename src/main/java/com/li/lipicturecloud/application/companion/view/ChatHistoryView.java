package com.li.lipicturecloud.application.companion.view;

import java.util.List;

/**
 * 伙伴对话历史（按时间正序）。
 */
public record ChatHistoryView(List<ChatMessageView> records) {
}
