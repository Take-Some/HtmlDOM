package dev.takesome.htmldom.runtime;

import dev.takesome.htmldom.dom.UiDomDocument;
import dev.takesome.htmldom.dom.UiDomElement;
import dev.takesome.htmldom.dom.UiDomTraversal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Runtime-facing HtmlDom document wrapper.
 *
 * <p>The wrapped {@link UiDomDocument} remains the canonical retained DOM tree. This
 * class adds the Helix-style runtime layer that is useful outside Minecraft: document
 * values, actions, structured environments and a neutral host bridge for scripts or
 * application code.</p>
 */
public final class HtmlDomDocument {
    private final UiDomDocument dom;
    private final LinkedHashMap<String, HtmlDomValueBinding> bindings = new LinkedHashMap<>();
    private final LinkedHashMap<String, HtmlDomAction> actions = new LinkedHashMap<>();
    private HtmlDomHostBridge bridge = HtmlDomHostBridge.NOOP;

    public HtmlDomDocument(UiDomDocument dom) {
        this.dom = dom == null ? emptyDocument() : dom;
    }

    public static HtmlDomDocument parse(String markup) {
        return new HtmlDomDocument(UiDomDocument.parse(markup));
    }

    public UiDomDocument dom() {
        return dom;
    }

    public UiDomDocument uiDocument() {
        return dom;
    }

    public UiDomElement root() {
        return dom.root();
    }

    public UiDomElement documentElement() {
        return dom.documentElement();
    }

    public UiDomElement renderRoot() {
        return dom.renderRoot();
    }

    public Optional<UiDomElement> getElementById(String id) {
        return dom.getElementById(id);
    }

    public Optional<UiDomElement> querySelector(String selector) {
        return dom.querySelector(selector);
    }

    public List<UiDomElement> querySelectorAll(String selector) {
        return dom.querySelectorAll(selector);
    }

    public List<UiDomElement> elements() {
        if (dom.rootOptional().isEmpty()) return List.of();
        return UiDomTraversal.depthFirstElements(dom.documentElement());
    }

    public HtmlDomDocument bridge(HtmlDomHostBridge bridge) {
        this.bridge = bridge == null ? HtmlDomHostBridge.NOOP : bridge;
        return this;
    }

    public HtmlDomHostBridge bridge() {
        return bridge;
    }

    public HtmlDomDocument bind(String name, HtmlDomValueBinding binding) {
        String key = normalizeName(name);
        if (!key.isBlank() && binding != null) {
            synchronized (bindings) {
                bindings.put(key, binding);
            }
        }
        return this;
    }

    public HtmlDomDocument values(Map<String, ?> values) {
        if (values == null || values.isEmpty()) return this;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            value(entry.getKey(), entry.getValue());
        }
        return this;
    }

    public HtmlDomDocument value(String name, Object value) {
        String key = normalizeName(name);
        if (key.isBlank()) return this;
        synchronized (bindings) {
            HtmlDomValueBinding binding = bindings.get(key);
            if (binding instanceof HtmlDomMemoryValueBinding) {
                binding.set(value);
            } else {
                bindings.put(key, new HtmlDomMemoryValueBinding(value));
            }
        }
        return this;
    }

    public HtmlDomDocument environment(Map<String, ?> environment) {
        return HtmlDomEnvironment.apply(this, environment);
    }

    public HtmlDomDocument environment(String json) {
        return envJson(json);
    }

    public HtmlDomDocument envJson(String json) {
        return HtmlDomJsonEnvironment.apply(this, json);
    }

    public HtmlDomValueBinding binding(String name) {
        synchronized (bindings) {
            return bindings.get(normalizeName(name));
        }
    }

    public Object value(String name) {
        HtmlDomValueBinding binding = binding(name);
        return binding == null ? null : binding.get();
    }

    public String displayValue(String name) {
        HtmlDomValueBinding binding = binding(name);
        return binding == null ? "" : binding.displayValue();
    }

    public Map<String, HtmlDomValueBinding> bindings() {
        synchronized (bindings) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
        }
    }

    public HtmlDomDocument action(String name, HtmlDomAction action) {
        String key = normalizeName(name);
        if (!key.isBlank() && action != null) {
            synchronized (actions) {
                actions.put(key, action);
            }
        }
        return this;
    }

    public HtmlDomAction action(String name) {
        synchronized (actions) {
            return actions.get(normalizeName(name));
        }
    }

    public Map<String, HtmlDomAction> actions() {
        synchronized (actions) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(actions));
        }
    }

    public boolean runAction(String name) {
        return runAction(name, "");
    }

    public boolean runAction(String name, String payload) {
        String key = normalizeName(name);
        if (key.isBlank()) return false;
        HtmlDomAction action = action(key);
        if (action == null) return false;
        action.run(new HtmlDomActionEvent(this, key, "", Objects.toString(payload, "")));
        return true;
    }

    private static UiDomDocument emptyDocument() {
        UiDomDocument document = new UiDomDocument();
        document.setRoot(document.createElement("document"));
        document.drainMutations();
        document.root().clearDirty();
        return document;
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }
}
