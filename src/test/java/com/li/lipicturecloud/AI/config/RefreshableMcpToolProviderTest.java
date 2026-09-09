package com.li.lipicturecloud.AI.config;

import com.li.lipicturecloud.application.airuntime.McpToolAccessDecider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshableMcpToolProviderTest {

    private static final String REVIEWED_SERVICE_CODE = "mxai-mcp-server";

    private McpToolAccessDecider decider;
    private RefreshableMcpToolProvider provider;

    @BeforeEach
    void setUp() {
        decider = mock(McpToolAccessDecider.class);
        provider = new RefreshableMcpToolProvider();
        ReflectionTestUtils.setField(provider, "mcpToolAccessDecider", decider);
        ReflectionTestUtils.setField(provider, "enabled", true);
        when(decider.isToolAllowed(eq(REVIEWED_SERVICE_CODE), anyString())).thenReturn(false);
    }

    @Test
    void pollingIsAllowedOnlyWhenTheStatusToolStillPassesTheWhitelist() {
        when(decider.isToolAllowed(REVIEWED_SERVICE_CODE, "get_task_status")).thenReturn(true);

        assertThat(provider.statusPollingAllowed()).isTrue();
        verify(decider).isToolAllowed(REVIEWED_SERVICE_CODE, "get_task_status");

        // 状态工具被停用：内部轮询必须 fail-closed。
        when(decider.isToolAllowed(REVIEWED_SERVICE_CODE, "get_task_status")).thenReturn(false);
        assertThat(provider.statusPollingAllowed()).isFalse();
    }

    @Test
    void missingDeciderNeverAllowsInternalPolling() {
        RefreshableMcpToolProvider deciderless = new RefreshableMcpToolProvider();
        ReflectionTestUtils.setField(deciderless, "mcpToolAccessDecider", null);
        ReflectionTestUtils.setField(deciderless, "enabled", true);

        // fail-closed：裁决器缺失同样拒绝，绝不 fail-open。
        assertThat(deciderless.statusPollingAllowed()).isFalse();
    }

    @Test
    void generationAllowedButStatusToolDisabledStopsPollingWithoutSleepingOrCallingMcp() {
        // 生成工具在 call 入口允许（已裁决过），但状态查询工具被停用：
        // Java 层内部轮询不得在后台绕过白名单调用 get_task_status。
        when(decider.isToolAllowed(REVIEWED_SERVICE_CODE, "generate_image")).thenReturn(true);
        when(decider.isToolAllowed(REVIEWED_SERVICE_CODE, "get_task_status")).thenReturn(false);

        // 包含 taskId 的生成结果会触发"自动等待完成"；状态工具禁用时应立即返回明确提示，
        // 不发生 50 秒初始等待，也不发起任何 MCP 调用。
        long started = System.currentTimeMillis();
        String result = provider.pollUntilComplete("任务序列号: 2079161218412580864，图片生成中。");
        long elapsed = System.currentTimeMillis() - started;

        assertThat(elapsed).isLessThan(5_000L);
        assertThat(result).contains("已停用");
        assertThat(result).contains("2079161218412580864");
        verify(decider).isToolAllowed(REVIEWED_SERVICE_CODE, "get_task_status");
        verify(decider, never()).isToolAllowed(REVIEWED_SERVICE_CODE, "generate_image");
    }

    @Test
    void statusPollingKeepsRecheckingTheWhitelistOnEveryCall() {
        when(decider.isToolAllowed(REVIEWED_SERVICE_CODE, "get_task_status")).thenReturn(false);

        // callMcpGetTaskStatus 自带裁决：即使轮询入口被绕过也不能调用停用工具。
        assertThat(provider.callMcpGetTaskStatus("123")).isNull();
        verify(decider).isToolAllowed(REVIEWED_SERVICE_CODE, "get_task_status");
    }
}
