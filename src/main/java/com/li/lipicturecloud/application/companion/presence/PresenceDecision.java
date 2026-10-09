package com.li.lipicturecloud.application.companion.presence;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;

/** Strict fail-closed output language. No tool/action/URL payload can pass this boundary. */
public record PresenceDecision(String action, String emote, String text) {
    private static final Set<String> FIELDS = Set.of("action", "emote", "text");
    private static final Set<String> EMOTES = Set.of("WAVE", "NOD", "SPARKLE");
    public static PresenceDecision silent() { return new PresenceDecision("SILENT", null, null); }

    public static PresenceDecision parse(String raw) {
        try {
            if (raw == null || raw.length() > 1024) throw new IllegalArgumentException();
            ObjectMapper mapper = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
            JsonNode node = mapper.readTree(raw);
            if (node == null || !node.isObject() || node.size() != 3) throw new IllegalArgumentException();
            node.fieldNames().forEachRemaining(key -> { if (!FIELDS.contains(key)) throw new IllegalArgumentException(); });
            String action = value(node, "action"), emote = value(node, "emote"), text = value(node, "text");
            boolean valid = switch (action == null ? "" : action) {
                case "SILENT" -> emote == null && text == null;
                case "EMOTE" -> EMOTES.contains(emote == null ? "" : emote) && text == null;
                case "SPEAK" -> emote == null && text != null && !text.isBlank()
                        && text.codePointCount(0, text.length()) <= 100
                        && text.codePoints().noneMatch(Character::isISOControl)
                        && !text.contains("<") && !text.contains(">") && !text.contains("://")
                        && !text.toLowerCase(java.util.Locale.ROOT).contains("www.");
                default -> false;
            };
            if (!valid) throw new IllegalArgumentException();
            return new PresenceDecision(action, emote, text);
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Invalid presence decision");
        }
    }
    private static String value(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || (!value.isNull() && !value.isTextual())) throw new IllegalArgumentException();
        return value.isNull() ? null : value.textValue();
    }
}
