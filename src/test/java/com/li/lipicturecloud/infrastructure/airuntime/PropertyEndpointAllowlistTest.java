package com.li.lipicturecloud.infrastructure.airuntime;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PropertyEndpointAllowlistTest {

    private final PropertyEndpointAllowlist allowlist = new PropertyEndpointAllowlist(
            List.of("deepseek.com", "api.openai.com", " DashScope.Aliyuncs.com "));

    @Test
    void allowsExactHostAndSubdomains() {
        assertThat(allowlist.isAllowed(URI.create("https://deepseek.com/v1"))).isTrue();
        assertThat(allowlist.isAllowed(URI.create("https://api.deepseek.com/v1"))).isTrue();
        assertThat(allowlist.isAllowed(URI.create("https://v1.api.deepseek.com/v1"))).isTrue();
        assertThat(allowlist.isAllowed(URI.create("https://api.openai.com/v1"))).isTrue();
        // 后缀大小写与配置里的空白会被规范化。
        assertThat(allowlist.isAllowed(URI.create("https://dashscope.aliyuncs.com/v1"))).isTrue();
    }

    @Test
    void rejectsSiblingDomainsAndNonHttpsSchemes() {
        assertThat(allowlist.isAllowed(URI.create("https://evildeepseek.com/v1"))).isFalse();
        assertThat(allowlist.isAllowed(URI.create("https://deepseek.com.evil.io/v1"))).isFalse();
        assertThat(allowlist.isAllowed(URI.create("http://api.deepseek.com/v1"))).isFalse();
    }

    @Test
    void rejectsNonHttpsAndNullHosts() {
        assertThat(allowlist.isAllowed(URI.create("file:///etc/passwd"))).isFalse();
        assertThat(allowlist.isAllowed(URI.create("https:///nohost"))).isFalse();
        assertThatThrownBy(() -> allowlist.isAllowed(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void emptyAllowlistIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new PropertyEndpointAllowlist(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PropertyEndpointAllowlist(List.of("  ")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
