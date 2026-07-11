package dev.takesome.htmldom.css;

import java.util.List;
import java.util.Locale;

/** Platform-neutral CSS mask image value. Rendering remains the responsibility of the host backend. */
public record UiMaskImage(Kind kind, String rawValue, String source) {
    private static final UiMaskImage NONE = new UiMaskImage(Kind.NONE, "none", "");

    public enum Kind {
        NONE,
        URL,
        LINEAR_GRADIENT,
        RADIAL_GRADIENT,
        CONIC_GRADIENT,
        IMAGE_SET,
        CUSTOM
    }

    public UiMaskImage {
        kind = kind == null ? Kind.NONE : kind;
        rawValue = rawValue == null ? "" : rawValue.trim();
        source = source == null ? "" : source.trim();
    }

    public static UiMaskImage none() {
        return NONE;
    }

    public static UiMaskImage parse(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty() || "none".equalsIgnoreCase(value)) return NONE;

        List<String> layers = UiCssTokenSplitter.splitTopLevelComma(value);
        String firstLayer = layers.isEmpty() ? value : layers.get(0).trim();
        String lower = firstLayer.toLowerCase(Locale.ROOT);

        if (lower.contains("url(")) {
            String source = UiCssUrlNormalizer.normalizeUrlValue(firstLayer);
            return source.isBlank() ? NONE : new UiMaskImage(Kind.URL, firstLayer, source);
        }
        if (lower.startsWith("linear-gradient(") || lower.startsWith("repeating-linear-gradient(")) {
            return new UiMaskImage(Kind.LINEAR_GRADIENT, firstLayer, "");
        }
        if (lower.startsWith("radial-gradient(") || lower.startsWith("repeating-radial-gradient(")) {
            return new UiMaskImage(Kind.RADIAL_GRADIENT, firstLayer, "");
        }
        if (lower.startsWith("conic-gradient(") || lower.startsWith("repeating-conic-gradient(")) {
            return new UiMaskImage(Kind.CONIC_GRADIENT, firstLayer, "");
        }
        if (lower.startsWith("image-set(") || lower.startsWith("-webkit-image-set(")) {
            return new UiMaskImage(Kind.IMAGE_SET, firstLayer, "");
        }
        return new UiMaskImage(Kind.CUSTOM, firstLayer, "");
    }

    public boolean active() {
        return kind != Kind.NONE;
    }

    public boolean resourceBacked() {
        return kind == Kind.URL && !source.isBlank();
    }
}
