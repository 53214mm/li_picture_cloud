package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.airuntime.ModelRouteDecision;
import com.li.lipicturecloud.domain.companion.CompanionChatMessage;

import java.util.List;
import java.util.Objects;

/**
 * 把一段结束的聊天整理成一条经历。失败时抛异常，调用方下一轮再试。
 */
public interface ChatEpisodeSummarizer {

    /**
     * @param route       本轮聊天用的模型路由（自己的 Key 优先，否则平台）
     * @param oldestFirst 这一段的消息（旧→新）
     */
    Summary summarize(ModelRouteDecision route, List<CompanionChatMessage> oldestFirst);

    /** @param text 经历内容；没有值得记住的事时为空串 */
    record Summary(String text, String modelProvider, String modelCode) {
        public Summary {
            Objects.requireNonNull(text, "text");
        }

        public static Summary nothing() {
            return new Summary("", null, null);
        }
    }
}
