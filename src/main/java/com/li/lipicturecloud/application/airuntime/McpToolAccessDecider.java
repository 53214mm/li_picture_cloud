package com.li.lipicturecloud.application.airuntime;

/**
 * MCP 工具访问裁决端口：伙伴能力目录只暴露平台审核通过且当前启用的工具。
 * 未配置服务、服务停用、工具不在白名单或白名单停用，一律不允许（fail-closed）。
 */
public interface McpToolAccessDecider {

    boolean isToolAllowed(String serviceCode, String toolName);
}
