package dev.takesome.htmldom.css;

import dev.takesome.htmldom.dom.UiDomElement;

/** Resolves mask-related computed style into a backend-neutral descriptor. */
public final class UiCssMaskResolver {
    private UiCssMaskResolver() {
    }

    public static UiCssMask resolve(UiDomElement element) {
        if (element == null) return UiCssMask.none();

        String imageValue = first(element, "mask-image", "-webkit-mask-image");
        String shorthand = first(element, "mask", "-webkit-mask");
        if (imageValue.isBlank()) imageValue = shorthand;

        UiMaskImage image = UiMaskImage.parse(imageValue);
        if (!image.active()) return UiCssMask.none();

        String modeValue = first(element, "mask-mode", "-webkit-mask-mode");
        String repeatValue = first(element, "mask-repeat", "-webkit-mask-repeat");
        String size = first(element, "mask-size", "-webkit-mask-size");
        String position = first(element, "mask-position", "-webkit-mask-position");
        String opacityValue = first(element, "mask-opacity", "-webkit-mask-opacity");
        String invertValue = first(element, "mask-invert", "-webkit-mask-invert");
        String sampleValue = first(element, "mask-sample-size", "mask-resolution");

        return new UiCssMask(
                image,
                UiMaskMode.parse(modeValue),
                repeatValue.isBlank() ? UiMaskRepeat.REPEAT : UiMaskRepeat.parse(repeatValue),
                size.isBlank() ? "auto" : size,
                position.isBlank() ? "0% 0%" : position,
                parseUnitInterval(opacityValue, 1.0D),
                parseBoolean(invertValue),
                parseInteger(sampleValue, 1)
        );
    }

    private static String first(UiDomElement element, String... properties) {
        for (String property : properties) {
            String value = element.style(property, "");
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private static double parseUnitInterval(String rawValue, double fallback) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) return fallback;
        try {
            double parsed;
            if (value.endsWith("%")) {
                parsed = Double.parseDouble(value.substring(0, value.length() - 1).trim()) / 100.0D;
            } else {
                parsed = Double.parseDouble(value);
            }
            return Math.max(0.0D, Math.min(1.0D, parsed));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim().toLowerCase(java.util.Locale.ROOT);
        return "true".equals(value) || "1".equals(value) || "yes".equals(value) || "on".equals(value);
    }

    private static int parseInteger(String rawValue, int fallback) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
