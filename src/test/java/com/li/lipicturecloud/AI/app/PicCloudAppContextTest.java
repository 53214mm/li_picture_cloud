package com.li.lipicturecloud.AI.app;

import com.li.lipicturecloud.AI.common.UserContextHolder;
import com.li.lipicturecloud.model.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;

class PicCloudAppContextTest {

    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    @Test
    void bindingMcpCallbackForAnotherUserDoesNotKeepThePreviousUser() {
        ToolCallback delegate = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("test").description("test").inputSchema("{}").build();
            }

            @Override
            public String call(String toolInput) {
                return String.valueOf(UserContextHolder.get().getId());
            }
        };
        User firstUser = user(1L);
        User secondUser = user(2L);

        ToolCallback firstBinding = PicCloudApp.bindMcpCallback(delegate, firstUser);
        ToolCallback secondBinding = PicCloudApp.bindMcpCallback(delegate, secondUser);

        assertThat(firstBinding.call("{}")).isEqualTo("1");
        assertThat(secondBinding.call("{}")).isEqualTo("2");
    }

    private static User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
