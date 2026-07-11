package dev.takesome.htmldom.css;

import java.util.Locale;

public enum UiMaskMode {
    MATCH_SOURCE,
    ALPHA,
    LUMINANCE;

    public static UiMaskMode parse(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "alpha" -> ALPHA;
            case "luminance" -> LUMINANCE;
            default -> MATCH_SOURCE;
        };
    }
}
