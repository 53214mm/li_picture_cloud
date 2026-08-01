package com.li.lipicturecloud.AI.tools;

import com.li.lipicturecloud.AI.common.UserContextHolder;
import com.li.lipicturecloud.AI.service.AiPictureSaveService;
import com.li.lipicturecloud.model.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AIGenerationToolTest {

    @Test
    void delegatesToSharedSaveServiceWithAuthenticatedUser() {
        AiPictureSaveService saveService = mock(AiPictureSaveService.class);
        AIGenerationTool tool = new AIGenerationTool(saveService);
        User user = new User();
        user.setId(42L);
        when(saveService.save("https://cdn.example.com/result.png", "海边", user))
                .thenReturn("保存成功");

        UserContextHolder.set(user);
        try {
            assertThat(tool.saveToMySpace("https://cdn.example.com/result.png", "海边"))
                    .isEqualTo("保存成功");
            verify(saveService).save("https://cdn.example.com/result.png", "海边", user);
        } finally {
            UserContextHolder.clear();
        }
    }
}
