package dev.takesome.htmldom.runtime;

import dev.takesome.htmldom.dom.UiDomElement;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Applies a platform-neutral structured environment to an {@link HtmlDomDocument}.
 *
 * <p>The supported top-level sections intentionally mirror common UI bootstrap data:</p>
 * <ul>
 *     <li>{@code values}, {@code env}, {@code bindings}: document values; nested maps are flattened with dots.</li>
 *     <li>{@code byId}, {@code ids}, {@code elements}: element attributes keyed by element id.</li>
 *     <li>{@code actions}: action attribute overrides keyed by element id.</li>
 * </ul>
 */
public final class HtmlDomEnvironment {
    private HtmlDomEnvironment() {
    }

    public static HtmlDomDocument apply(HtmlDomDocument document, Map<String, ?> environment) {
        if (document == null || environment == null || environment.isEmpty()) return document;

        applyValuesSection(document, environment.get("values"));
        applyValuesSection(document, environment.get("env"));
        applyValuesSection(document, environment.get("bindings"));

        applyElementsSection(document, environment.get("byId"));
        applyElementsSection(document, environment.get("ids"));
        applyElementsSection(document, environment.get("elements"));

        applyActionsSection(document, environment.get("actions"));
        return document;
    }

    private static void applyValuesSection(HtmlDomDocument document, Object section) {
        Map<String, ?> values = asMap(section);
        if (values.isEmpty()) return;
        flattenValues(document, "", values);
    }

    private static void flattenValues(HtmlDomDocument document, String prefix, Map<String, ?> values) {
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = cleanId(entry.getKey());
            if (key.isEmpty()) continue;

            String name = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = entry.getValue();
            Map<String, ?> nested = asMap(value);
            if (!nested.isEmpty()) {
                flattenValues(document, name, nested);
            } else {
                document.value(name, value);
            }
        }
    }

    private static void applyElementsSection(HtmlDomDocument document, Object section) {
        Map<String, ?> elements = asMap(section);
        if (elements.isEmpty()) return;

        for (Map.Entry<String, ?> entry : elements.entrySet()) {
            String id = cleanId(entry.getKey());
            if (id.isEmpty()) continue;
            Map<String, ?> data = asMap(entry.getValue());
            if (data.isEmpty()) continue;

            UiDomElement element = document.getElementById(id).orElse(null);
            if (element != null) applyElement(document, element, data);
        }
    }

    private static void applyElement(HtmlDomDocument document, UiDomElement element, Map<String, ?> data) {
        for (Map.Entry<String, ?> entry : data.entrySet()) {
            String key = cleanAttribute(entry.getKey());
            if (key.isEmpty()) continue;
            Object value = entry.getValue();

            if ("$text".equals(key) || "text-content".equals(key) || "textcontent".equals(key)) {
                setText(document, element, Objects.toString(value, ""));
                continue;
            }
            if ("class+".equals(key) || "add-class".equals(key) || "addclass".equals(key)) {
                addClasses(element, Objects.toString(value, ""));
                continue;
            }
            if ("class-".equals(key) || "remove-class".equals(key) || "removeclass".equals(key)) {
                removeClasses(element, Objects.toString(value, ""));
                continue;
            }
            if ("attributes".equals(key)) {
                applyAttributes(element, asMap(value));
                continue;
            }
            if (value == null) {
                element.removeAttribute(key);
            } else {
                element.setAttribute(key, scalarString(value));
            }
        }
    }

    private static void applyAttributes(UiDomElement element, Map<String, ?> attributes) {
        for (Map.Entry<String, ?> attribute : attributes.entrySet()) {
            String name = cleanAttribute(attribute.getKey());
            if (name.isEmpty()) continue;
            if (attribute.getValue() == null) element.removeAttribute(name);
            else element.setAttribute(name, scalarString(attribute.getValue()));
        }
    }

    private static void applyActionsSection(HtmlDomDocument document, Object section) {
        Map<String, ?> actions = asMap(section);
        if (actions.isEmpty()) return;

        for (Map.Entry<String, ?> entry : actions.entrySet()) {
            String id = cleanId(entry.getKey());
            if (id.isEmpty()) continue;
            UiDomElement element = document.getElementById(id).orElse(null);
            if (element == null) continue;
            Object action = entry.getValue();
            if (action == null) element.removeAttribute("action");
            else element.setAttribute("action", scalarString(action));
        }
    }

    private static void setText(HtmlDomDocument document, UiDomElement element, String text) {
        element.clearChildren();
        if (!text.isEmpty()) element.appendChild(document.dom().createText(text));
    }

    private static void addClasses(UiDomElement element, String classes) {
        for (String token : classes.trim().split("\\s+")) {
            if (!token.isBlank()) element.classList().add(token);
        }
    }

    private static void removeClasses(UiDomElement element, String classes) {
        for (String token : classes.trim().split("\\s+")) {
            if (!token.isBlank()) element.classList().remove(token);
        }
    }

    private static String scalarString(Object value) {
        if (value == null) return "";
        if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Character) {
            return String.valueOf(value);
        }
        return HtmlDomJsonEnvironment.stringify(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> asMap(Object value) {
        if (!(value instanceof Map<?, ?> raw) || raw.isEmpty()) return Map.of();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return (Map<String, ?>) result;
    }

    private static String cleanId(String id) {
        String clean = id == null ? "" : id.trim();
        while (clean.startsWith("#")) clean = clean.substring(1).trim();
        return clean;
    }

    private static String cleanAttribute(String key) {
        return key == null ? "" : key.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
