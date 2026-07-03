package dev.takesome.htmldom.runtime;

/** Mutable value binding owned by an HtmlDom runtime document. */
public interface HtmlDomValueBinding {
    Object get();

    void set(Object value);

    default boolean asBoolean(boolean fallback) {
        Object value = get();
        if (value instanceof Boolean booleanValue) return booleanValue;
        if (value instanceof Number numberValue) return numberValue.doubleValue() != 0.0D;
        if (value instanceof String stringValue) {
            String raw = stringValue.trim();
            if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("on") || raw.equals("1")) return true;
            if (raw.equalsIgnoreCase("false") || raw.equalsIgnoreCase("off") || raw.equals("0")) return false;
        }
        return fallback;
    }

    default int asInt(int fallback) {
        Object value = get();
        if (value instanceof Number numberValue) return numberValue.intValue();
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue.trim());
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    default double asDouble(double fallback) {
        Object value = get();
        if (value instanceof Number numberValue) return numberValue.doubleValue();
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue.trim());
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    default String displayValue() {
        Object value = get();
        if (value == null) return "";
        if (value instanceof Double || value instanceof Float) {
            return String.format(java.util.Locale.ROOT, "%.1f", ((Number) value).doubleValue());
        }
        return String.valueOf(value);
    }
}
