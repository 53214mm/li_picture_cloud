package com.li.lipicturecloud.AI.service;

import com.li.lipicturecloud.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts generated image URLs from MCP results and delegates persistence.
 */
@Component
@RequiredArgsConstructor
public class McpGeneratedImageHandler {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\\n<>\"{}]+");

    private final AiPictureSaveService saveService;

    public String appendSaveResult(String mcpText, User user) {
        if (mcpText == null) {
            return null;
        }
        Matcher matcher = URL_PATTERN.matcher(mcpText);
        if (!matcher.find()) {
            return mcpText;
        }
        String saveResult = saveService.save(matcher.group(), "AI生成", user);
        return mcpText + "\n" + saveResult;
    }
}
