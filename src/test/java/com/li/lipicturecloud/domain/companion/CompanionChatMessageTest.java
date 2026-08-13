package com.li.lipicturecloud.domain.companion;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanionChatMessageTest {

    private static final Instant NOW = Instant.parse("2026-08-14T08:00:00Z");

    @Test
    void userMessageStartsWithNoModelFields() {
        CompanionChatMessage message = CompanionChatMessage.user(11L, 7L, "  你好，伙伴  ", NOW);

        assertThat(message.role()).isEqualTo(CompanionChatRole.USER);
        assertThat(message.content()).isEqualTo("你好，伙伴");
        assertThat(message.modelProvider()).isNull();
        assertThat(message.modelCode()).isNull();
    }

    @Test
    void companionReplyCarriesModelFacts() {
        CompanionChatMessage message = CompanionChatMessage.companion(11L, 7L, "我在听。",
                "dashscope", "qwen-max", NOW);

        assertThat(message.role()).isEqualTo(CompanionChatRole.COMPANION);
        assertThat(message.modelProvider()).isEqualTo("dashscope");
        assertThat(message.modelCode()).isEqualTo("qwen-max");
    }

    @Test
    void rejectsEmptyOversizedOrControlCharacters() {
        assertThatThrownBy(() -> CompanionChatMessage.user(11L, 7L, "   ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompanionChatMessage.user(11L, 7L, "长".repeat(1001), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompanionChatMessage.user(11L, 7L, "带\u0000控制字符", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompanionChatMessage.user(11L, 7L, null, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsInvalidIdentityAndModelFields() {
        assertThatThrownBy(() -> CompanionChatMessage.user(0L, 7L, "内容", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompanionChatMessage(null, 11L, 7L, CompanionChatRole.USER,
                "内容", "很长的提供商名称".repeat(10), null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompanionChatMessage(null, 11L, 7L, null,
                "内容", null, null, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void withIdGuardsItsTransition() {
        CompanionChatMessage message = CompanionChatMessage.user(11L, 7L, "内容", NOW);

        assertThat(message.withId(51L).id()).isEqualTo(51L);
        assertThatThrownBy(() -> message.withId(0L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> message.withId(51L).withId(52L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsOrdinaryLinkTextInsideChat() {
        CompanionChatMessage message = CompanionChatMessage.user(11L, 7L,
                "帮我看看这个链接 https://example.com 的图片", NOW);

        assertThat(message.content()).isEqualTo("帮我看看这个链接 https://example.com 的图片");
    }
}
