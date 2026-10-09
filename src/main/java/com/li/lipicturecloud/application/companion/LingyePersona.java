package com.li.lipicturecloud.application.companion;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Versioned, reviewable persona shared by ordinary chat and bounded presence decisions. */
public final class LingyePersona {
    public static final String VERSION = "lingye-persona-v1";
    private static final String PROMPT = load();

    private LingyePersona() { }

    public static String systemPrompt() {
        return PROMPT;
    }

    private static String load() {
        try {
            return new ClassPathResource("companion/" + VERSION + ".txt")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Missing Lingye persona resource", failure);
        }
    }
}
