package com.li.lipicturecloud.AI.service;

import com.li.lipicturecloud.model.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class McpGeneratedImageHandlerTest {

    private AiPictureSaveService saveService;
    private McpGeneratedImageHandler handler;

    @BeforeEach
    void setUp() {
        saveService = mock(AiPictureSaveService.class);
        handler = new McpGeneratedImageHandler(saveService);
    }

    @Test
    void appendsSharedSaveResultForGeneratedImageUrl() {
        User user = user(42L);
        when(saveService.save("https://cdn.example.com/result.png", "AI生成", user))
                .thenReturn("已保存到空间「个人空间」，图片 ID: 99");

        String result = handler.appendSaveResult(
                "生成成功：https://cdn.example.com/result.png", user);

        assertThat(result).isEqualTo("生成成功：https://cdn.example.com/result.png\n已保存到空间「个人空间」，图片 ID: 99");
        verify(saveService).save("https://cdn.example.com/result.png", "AI生成", user);
    }

    @Test
    void leavesTextWithoutUrlUnchanged() {
        String text = "任务仍在处理中";

        assertThat(handler.appendSaveResult(text, user(42L))).isEqualTo(text);
        verifyNoInteractions(saveService);
    }

    private static User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
