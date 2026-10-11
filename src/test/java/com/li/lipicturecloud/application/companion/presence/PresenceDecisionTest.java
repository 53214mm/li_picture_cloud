package com.li.lipicturecloud.application.companion.presence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class PresenceDecisionTest {
    @ParameterizedTest
    @ValueSource(strings = {
        "{\"action\":\"SILENT\",\"emote\":null,\"text\":null}",
        "{\"action\":\"EMOTE\",\"emote\":\"WAVE\",\"text\":null}",
        "{\"action\":\"SPEAK\",\"emote\":null,\"text\":\"收到！\"}"
    })
    void acceptsOnlySmallDecisionLanguage(String raw) { assertThat(PresenceDecision.parse(raw)).isNotNull(); }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "null", "[]", "```json {} ```",
        "{\"action\":\"EXECUTE\",\"emote\":null,\"text\":null}",
        "{\"action\":\"SPEAK\",\"emote\":null,\"text\":null}",
        "{\"action\":\"SILENT\",\"emote\":null,\"text\":\"not silent\"}",
        "{\"action\":\"EMOTE\",\"emote\":\"DELETE\",\"text\":null}",
        "{\"action\":\"SILENT\",\"emote\":null,\"text\":null,\"tool\":\"delete\"}",
        "{\"action\":\"SILENT\",\"emote\":null,\"text\":null} {}",
        "{\"action\":\"SILENT\",\"action\":\"SILENT\",\"emote\":null,\"text\":null}",
        "{\"action\":1,\"emote\":null,\"text\":null}",
        "{\"action\":\"SPEAK\",\"emote\":null,\"text\":\"<script>\"}",
        "{\"action\":\"SPEAK\",\"emote\":null,\"text\":\"https://evil.example\"}",
        "{\"action\":\"SPEAK\",\"emote\":null,\"text\":\"www.evil.example\"}",
        "{\"action\":\"SPEAK\",\"emote\":null,\"text\":\"line\\nline\"}",
        "{\"action\":\"SPEAK\",\"emote\":\"WAVE\",\"text\":\"hello\"}"
    })
    void rejectsInvalidOutputWithoutRepair(String raw) {
        assertThatThrownBy(() -> PresenceDecision.parse(raw)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsLengthAndBlank() {
        for (String text : new String[]{"x".repeat(101), " ", "x".repeat(1100)})
            assertThatThrownBy(() -> PresenceDecision.parse("{\"action\":\"SPEAK\",\"emote\":null,\"text\":\"" + text + "\"}"))
                    .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PresenceDecision.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
