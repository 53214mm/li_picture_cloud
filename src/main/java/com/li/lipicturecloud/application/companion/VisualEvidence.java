package com.li.lipicturecloud.application.companion;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Objective model evidence. None of the fields represents a user statement or a companion reaction.
 *
 * @param visibleObservations     what is visible in the picture
 * @param uncertainInterpretation what the picture may mean or express; always a hedged guess
 * @param possibleIdentity        which character, work, mascot, logo or place it may show; a hedged guess
 */
public record VisualEvidence(String visibleObservations, String uncertainInterpretation, String possibleIdentity) {
    private static final Pattern FIRST_PERSON = Pattern.compile("我|本人|绫页|(?i)\\b(i|my|me|we|our|us)\\b");
    /** Quoted titles and names (《我的世界》, 「我」) are content, not first-person narration. */
    private static final Pattern QUOTED = Pattern.compile("《[^》]*》|「[^」]*」|“[^”]*”|\"[^\"]*\"");

    public static final int MAX_OBSERVATION = 150;
    public static final int MAX_INTERPRETATION = 70;
    public static final int MAX_IDENTITY = 50;

    public VisualEvidence {
        visibleObservations = validate(visibleObservations, 20, MAX_OBSERVATION, "visibleObservations");
        uncertainInterpretation = validate(uncertainInterpretation, 0, MAX_INTERPRETATION, "uncertainInterpretation");
        possibleIdentity = validate(possibleIdentity, 0, MAX_IDENTITY, "possibleIdentity");
    }

    /** Evidence produced before the identity field existed. */
    public VisualEvidence(String visibleObservations, String uncertainInterpretation) {
        this(visibleObservations, uncertainInterpretation, "");
    }

    /** At most 297 code points, within the 300-character memory and growth-reason budget. */
    public String summary() {
        return "可见观察：" + visibleObservations
                + (uncertainInterpretation.isEmpty() ? "" : " 可能的意义（推测）：" + uncertainInterpretation)
                + (possibleIdentity.isEmpty() ? "" : " 可能的角色或出处（推测）：" + possibleIdentity);
    }

    private static String validate(String value, int minimum, int maximum, String name) {
        value = Objects.requireNonNull(value, name).strip();
        int length = value.codePointCount(0, value.length());
        String lower = value.toLowerCase(Locale.ROOT);
        if (length < minimum || length > maximum || value.codePoints().anyMatch(Character::isISOControl)
                || lower.contains("http://") || lower.contains("https://") || lower.contains("www.")
                || FIRST_PERSON.matcher(QUOTED.matcher(value).replaceAll("")).find()) {
            throw new IllegalArgumentException(name + " must be bounded objective plain text");
        }
        return value;
    }
}
