package dev.takesome.htmldom.css;

import static dev.takesome.htmldom.support.validation.HtmlDomValidator.trimToEmpty;

/** Normalizes CSS url(...) values to plain resource identifiers without CSS quotes/wrappers. */
public final class UiCssUrlNormalizer {
    private UiCssUrlNormalizer() {}

    public static String normalizeUrlValue(String rawValue) {
        String value = trimToEmpty(rawValue);
        if (value.isBlank()) return "";
        String url = firstUrl(value);
        if (!url.isBlank()) return stripQuotes(url);
        return stripQuotes(value);
    }

    public static String firstUrl(String rawValue) {
        String value = trimToEmpty(rawValue);
        if (value.isBlank()) return "";
        int start = indexOfUrl(value);
        if (start < 0) return "";
        int i = start + 4;
        StringBuilder out = new StringBuilder();
        char quote = 0;
        int depth = 0;
        for (; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (quote != 0) {
                out.append(ch);
                if (ch == quote) quote = 0;
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                out.append(ch);
                continue;
            }
            if (ch == '(') {
                depth++;
                out.append(ch);
                continue;
            }
            if (ch == ')') {
                if (depth == 0) break;
                depth--;
                out.append(ch);
                continue;
            }
            out.append(ch);
        }
        return out.toString().trim();
    }

    public static String stripQuotes(String rawValue) {
        String value = trimToEmpty(rawValue);
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1).trim();
            }
        }
        return value;
    }

    private static int indexOfUrl(String value) {
        int len = value.length();
        char quote = 0;
        for (int i = 0; i <= len - 4; i++) {
            char ch = value.charAt(i);
            if (quote != 0) {
                if (ch == quote) quote = 0;
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                continue;
            }
            if ((ch == 'u' || ch == 'U')
                    && (value.charAt(i + 1) == 'r' || value.charAt(i + 1) == 'R')
                    && (value.charAt(i + 2) == 'l' || value.charAt(i + 2) == 'L')
                    && value.charAt(i + 3) == '(') {
                return i;
            }
        }
        return -1;
    }
}
