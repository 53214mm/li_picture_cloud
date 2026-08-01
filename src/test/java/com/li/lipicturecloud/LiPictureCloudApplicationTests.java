package com.li.lipicturecloud;

import com.li.lipicturecloud.AI.config.RefreshableMcpToolProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class LiPictureCloudApplicationTests {

    @Autowired
    private Environment environment;

    @Autowired
    private RefreshableMcpToolProvider mcpToolProvider;

    @Test
    void contextLoads() {
        assertThat(Arrays.asList(environment.getActiveProfiles())).containsExactly("test");
        assertThat(environment.getProperty("app.mcp.enabled", Boolean.class)).isFalse();
        assertThat(mcpToolProvider.getToolCallbacks()).isEmpty();
    }

}
