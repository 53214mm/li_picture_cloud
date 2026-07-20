package com.li.lipicturecloud.AI.common;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * URL 安全校验工具 — 防止 SSRF 攻击
 * <p>
 * 校验逻辑：协议白名单（仅 http/https）+ DNS 解析后过滤私有/保留 IP 地址。
 */
@Slf4j
public final class UrlValidator {

    private static final Set<String> ALLOWED_PROTOCOLS = Set.of("http", "https");

    /** 私有/保留 IP 前缀范围 */
    private static boolean isPrivateOrReserved(InetAddress addr) {
        byte[] octets = addr.getAddress();
        if (octets.length != 4) return true; // 仅允许 IPv4

        int b0 = octets[0] & 0xFF;
        int b1 = octets[1] & 0xFF;

        // 10.0.0.0/8
        if (b0 == 10) return true;
        // 172.16.0.0/12
        if (b0 == 172 && b1 >= 16 && b1 <= 31) return true;
        // 192.168.0.0/16
        if (b0 == 192 && b1 == 168) return true;
        // 127.0.0.0/8 (loopback)
        if (b0 == 127) return true;
        // 169.254.0.0/16 (link-local，含云元数据)
        if (b0 == 169 && b1 == 254) return true;
        // 0.0.0.0/8
        if (b0 == 0) return true;

        return false;
    }

    /**
     * 校验 URL 是否安全（仅允许公网 http/https，拒绝内网地址）
     *
     * @param urlStr 待校验的 URL 字符串
     * @return 校验通过返回 true
     * @throws IllegalArgumentException 如果 URL 不安全
     */
    public static boolean validate(String urlStr) {
        if (urlStr == null || urlStr.isBlank()) {
            throw new IllegalArgumentException("URL 不能为空");
        }

        URL url;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("URL 格式不正确: " + urlStr);
        }

        // 1. 协议白名单
        String protocol = url.getProtocol().toLowerCase();
        if (!ALLOWED_PROTOCOLS.contains(protocol)) {
            log.warn("[SSRF拦截] 禁止的协议: {} (URL: {})", protocol, urlStr);
            throw new IllegalArgumentException("仅支持 http/https 协议的 URL");
        }

        // 2. DNS 解析 + 私有 IP 过滤
        String host = url.getHost();
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (isPrivateOrReserved(addr)) {
                log.warn("[SSRF拦截] 禁止访问内网地址: {} (URL: {})", addr.getHostAddress(), urlStr);
                throw new IllegalArgumentException("不允许访问内网地址");
            }
        } catch (UnknownHostException e) {
            log.warn("[SSRF警告] DNS 解析失败: {} (URL: {})", host, urlStr);
            throw new IllegalArgumentException("无法解析目标地址: " + host);
        }

        return true;
    }

    /**
     * 校验文件名是否安全（无路径遍历风险）
     *
     * @param fileName 待校验的文件名
     * @throws IllegalArgumentException 如果文件名不安全
     */
    public static void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("文件名包含非法路径字符");
        }
    }
}
