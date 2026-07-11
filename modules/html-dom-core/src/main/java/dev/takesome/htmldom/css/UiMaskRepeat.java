package dev.takesome.htmldom.css;

import java.util.Locale;

public enum UiMaskRepeat {
    REPEAT,
    REPEAT_X,
    REPEAT_Y,
    NO_REPEAT,
    SPACE,
    ROUND;

    public static UiMaskRepeat parse(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "repeat-x" -> REPEAT_X;
            case "repeat-y" -> REPEAT_Y;
            case "no-repeat" -> NO_REPEAT;
            case "space" -> SPACE;
            case "round" -> ROUND;
            default -> REPEAT;
        };
    }
}
