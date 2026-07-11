package dev.takesome.htmldom.runtime;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Zero-dependency JSON adapter for {@link HtmlDomEnvironment}. */
public final class HtmlDomJsonEnvironment {
    private HtmlDomJsonEnvironment() {
    }

    public static HtmlDomDocument apply(HtmlDomDocument document, String json) {
        if (document == null || json == null || json.isBlank()) return document;
        return HtmlDomEnvironment.apply(document, parseObject(json));
    }

    public static Map<String, Object> parseObject(String json) {
        Object value = new Parser(json).parse();
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("HtmlDom JSON environment root must be an object");
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    /** Serializes environment values without requiring a third-party JSON library. */
    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        writeJson(out, value);
        return out.toString();
    }

    private static void writeJson(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String || value instanceof Character || value instanceof Enum<?>) {
            writeString(out, String.valueOf(value));
        } else if (value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Number number) {
            if (number instanceof Double d && (!Double.isFinite(d))) out.append("null");
            else if (number instanceof Float f && (!Float.isFinite(f))) out.append("null");
            else out.append(number);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                writeString(out, String.valueOf(entry.getKey()));
                out.append(':');
                writeJson(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof Collection<?> collection) {
            out.append('[');
            boolean first = true;
            for (Object item : collection) {
                if (!first) out.append(',');
                first = false;
                writeJson(out, item);
            }
            out.append(']');
        } else if (value.getClass().isArray()) {
            out.append('[');
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (i > 0) out.append(',');
                writeJson(out, java.lang.reflect.Array.get(value, i));
            }
            out.append(']');
        } else {
            writeString(out, String.valueOf(value));
        }
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch));
                    else out.append(ch);
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String source;
        private int index;

        private Parser(String source) {
            this.source = source == null ? "" : source;
        }

        private Object parse() {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (index != source.length()) error("Unexpected trailing content");
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (index >= source.length()) error("Expected JSON value");
            return switch (source.charAt(index)) {
                case '{' -> parseObjectValue();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObjectValue() {
            expect('{');
            LinkedHashMap<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (consume('}')) return object;
            while (true) {
                skipWhitespace();
                if (peek() != '"') error("Expected object key");
                String key = parseString();
                skipWhitespace();
                expect(':');
                object.put(key, parseValue());
                skipWhitespace();
                if (consume('}')) return object;
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            ArrayList<Object> array = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) return array;
            while (true) {
                array.add(parseValue());
                skipWhitespace();
                if (consume(']')) return array;
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (index < source.length()) {
                char ch = source.charAt(index++);
                if (ch == '"') return out.toString();
                if (ch != '\\') {
                    if (ch < 0x20) error("Control character in string");
                    out.append(ch);
                    continue;
                }
                if (index >= source.length()) error("Unterminated escape sequence");
                char escaped = source.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> appendUnicode(out);
                    default -> error("Invalid escape sequence");
                }
            }
            error("Unterminated string");
            return "";
        }

        private void appendUnicode(StringBuilder out) {
            int code = parseHexQuad();
            char first = (char) code;
            if (Character.isHighSurrogate(first)) {
                if (index + 1 >= source.length() || source.charAt(index) != '\\' || source.charAt(index + 1) != 'u') {
                    error("High surrogate must be followed by a low surrogate");
                }
                index += 2;
                char second = (char) parseHexQuad();
                if (!Character.isLowSurrogate(second)) error("Invalid low surrogate");
                out.appendCodePoint(Character.toCodePoint(first, second));
            } else if (Character.isLowSurrogate(first)) {
                error("Unexpected low surrogate");
            } else {
                out.append(first);
            }
        }

        private int parseHexQuad() {
            if (index + 4 > source.length()) error("Incomplete unicode escape");
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(source.charAt(index++), 16);
                if (digit < 0) error("Invalid unicode escape");
                value = (value << 4) | digit;
            }
            return value;
        }

        private Object parseNumber() {
            int start = index;
            if (consume('-')) {
                if (index >= source.length()) error("Incomplete number");
            }
            if (consume('0')) {
                if (index < source.length() && Character.isDigit(source.charAt(index))) error("Leading zero in number");
            } else {
                readDigits(true);
            }
            boolean decimal = false;
            if (consume('.')) {
                decimal = true;
                readDigits(true);
            }
            char next = peek();
            if (next == 'e' || next == 'E') {
                decimal = true;
                index++;
                if (peek() == '+' || peek() == '-') index++;
                readDigits(true);
            }
            if (start == index) error("Expected JSON value");

            String token = source.substring(start, index);
            try {
                if (!decimal) {
                    long value = Long.parseLong(token);
                    if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) return (int) value;
                    return value;
                }
                BigDecimal value = new BigDecimal(token);
                double doubleValue = value.doubleValue();
                if (!Double.isFinite(doubleValue)) return value;
                return doubleValue;
            } catch (NumberFormatException error) {
                throw failure("Invalid number", error);
            }
        }

        private void readDigits(boolean required) {
            int start = index;
            while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
            if (required && start == index) error("Expected digit");
        }

        private Object parseLiteral(String literal, Object value) {
            if (!source.regionMatches(index, literal, 0, literal.length())) error("Invalid literal");
            index += literal.length();
            return value;
        }

        private void skipWhitespace() {
            while (index < source.length()) {
                char ch = source.charAt(index);
                if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') index++;
                else break;
            }
        }

        private boolean consume(char expected) {
            if (peek() != expected) return false;
            index++;
            return true;
        }

        private void expect(char expected) {
            if (!consume(expected)) error("Expected '" + expected + "'");
        }

        private char peek() {
            return index < source.length() ? source.charAt(index) : '\0';
        }

        private void error(String message) {
            throw failure(message, null);
        }

        private IllegalArgumentException failure(String message, Throwable cause) {
            int start = Math.max(0, index - 16);
            int end = Math.min(source.length(), index + 16);
            String near = source.substring(start, end).replace("\n", "\\n").replace("\r", "\\r");
            String text = message + " at character " + index + (near.isEmpty() ? "" : " near `" + near + "`");
            return cause == null ? new IllegalArgumentException(text) : new IllegalArgumentException(text, cause);
        }
    }
}
