package com.li.lipicturecloud.application.companion;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Objective model evidence. Neither field represents a user statement or a companion reaction. */
public record VisualEvidence(String visibleObservations, String uncertainInterpretation) {
    private static final Pattern FIRST_PERSON = Pattern.compile("我|本人|绫页|(?i)\\b(i|my|me|we|our|us)\\b");

    public VisualEvidence {
        visibleObservations = validate(visibleObservations, 20, 160, "visibleObservations");
        uncertainInterpretation = validate(uncertainInterpretation, 0, 60, "uncertainInterpretation");
    }

    public String summary() {
        return "可见观察：" + visibleObservations
                + (uncertainInterpretation.isEmpty() ? "" : " 不确定解读：" + uncertainInterpretation);
    }

    private static String validate(String value, int minimum, int maximum, String name) {
        value = Objects.requireNonNull(value, name).strip();
        int length = value.codePointCount(0, value.length());
        String lower = value.toLowerCase(Locale.ROOT);
        if (length < minimum || length > maximum || value.codePoints().anyMatch(Character::isISOControl)
                || lower.contains("http://") || lower.contains("https://") || lower.contains("www.")
                || FIRST_PERSON.matcher(value).find()) {
            throw new IllegalArgumentException(name + " must be bounded objective plain text");
        }
        return value;
    }
}
