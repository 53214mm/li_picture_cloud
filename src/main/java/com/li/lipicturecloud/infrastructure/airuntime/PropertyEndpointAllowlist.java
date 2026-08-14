package com.li.lipicturecloud.infrastructure.airuntime;

import com.li.lipicturecloud.application.airuntime.EndpointAllowlist;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 基于主机后缀名单的端点白名单。仅接受 HTTPS，且主机必须精确等于某个后缀
 * 或为其子域（点边界判定，防止 evildeepseek.com 这类旁系域名混入）。
 */
public class PropertyEndpointAllowlist implements EndpointAllowlist {

    private final List<String> allowedHostSuffixes;

    public PropertyEndpointAllowlist(List<String> allowedHostSuffixes) {
        this.allowedHostSuffixes = Objects.requireNonNull(allowedHostSuffixes, "allowedHostSuffixes")
                .stream()
                .map(String::trim)
                .map(suffix -> suffix.toLowerCase(Locale.ROOT))
                .filter(suffix -> !suffix.isEmpty())
                .toList();
        if (this.allowedHostSuffixes.isEmpty()) {
            throw new IllegalArgumentException("endpoint allowlist must not be empty");
        }
    }

    @Override
    public boolean isAllowed(URI endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        if (!"https".equalsIgnoreCase(endpoint.getScheme())) {
            return false;
        }
        String host = endpoint.getHost();
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        for (String suffix : allowedHostSuffixes) {
            if (normalized.equals(suffix)) {
                return true;
            }
            if (normalized.endsWith("." + suffix) && normalized.length() > suffix.length() + 1) {
                return true;
            }
        }
        return false;
    }
}
