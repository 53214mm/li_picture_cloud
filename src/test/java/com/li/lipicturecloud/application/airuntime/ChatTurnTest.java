package com.li.lipicturecloud.application.airuntime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatTurnTest {

    @Test
    void factoriesBuildValidTurns() {
        assertThat(ChatTurn.system("提示").role()).isEqualTo("system");
        assertThat(ChatTurn.user("你好").content()).isEqualTo("你好");
        assertThat(ChatTurn.assistant("回复").role()).isEqualTo("assistant");
    }

    @Test
    void rejectsUnknownRolesAndNullContent() {
        assertThatThrownBy(() -> new ChatTurn("tool", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatTurn("system", null)).isInstanceOf(NullPointerException.class);
    }
}
