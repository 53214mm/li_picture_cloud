package com.li.lipicturecloud.application.airuntime.view;

/**
 * 识图联网开关的展示视图。
 *
 * @param forcedSearch 用户是否开启了强制联网
 * @param supported    当前识图模型是否真的能联网（只有百炼千问支持带图联网）
 * @param modelCode    当前识图使用的模型
 */
public record VisionSearchView(boolean forcedSearch, boolean supported, String modelCode) {
}
