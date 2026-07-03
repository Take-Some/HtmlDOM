package dev.takesome.htmldom.scripting.lua;

import dev.takesome.htmldom.dom.UiDomDocument;
import dev.takesome.htmldom.dom.UiDomElement;
import dev.takesome.htmldom.dom.UiDomTraversal;
import dev.takesome.htmldom.runtime.HtmlDomActionEvent;
import dev.takesome.htmldom.runtime.HtmlDomDocument;
import dev.takesome.htmldom.runtime.HtmlDomScriptSourceResolver;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lua runtime for HtmlDom. Lua is the desktop replacement for browser JavaScript. */
public final class HtmlDomLuaRuntime {
    private final HtmlDomDocument document;
    private final Globals globals;
    private final Map<String, LuaValue> actionHandlers = new LinkedHashMap<>();
    private final List<PrefixHandler> prefixHandlers = new ArrayList<>();

    public HtmlDomLuaRuntime(UiDomDocument document) {
        this(new HtmlDomDocument(document));
    }

    public HtmlDomLuaRuntime(HtmlDomDocument document) {
        if (document == null) throw new IllegalArgumentException("document must not be null");
        this.document = document;
        this.globals = JsePlatform.standardGlobals();
        HtmlDomLuaDomBindings.install(this, globals, document);
    }

    /** Backward-compatible access to the retained DOM tree. */
    public UiDomDocument document() {
        return document.dom();
    }

    public HtmlDomDocument htmlDocument() {
        return document;
    }

    public Globals globals() {
        return globals;
    }

    public void on(String action, LuaValue handler) {
        String key = normalizeAction(action);
        if (!key.isBlank() && handler != null && handler.isfunction()) {
            actionHandlers.put(key, handler);
        }
    }

    public void onPrefix(String prefix, LuaValue handler) {
        String key = prefix == null ? "" : prefix.trim();
        if (!key.isBlank() && handler != null && handler.isfunction()) {
            prefixHandlers.add(new PrefixHandler(key, handler));
        }
    }

    public boolean dispatchAction(String action) {
        return dispatchAction(action, "");
    }

    public boolean dispatchAction(String action, String payload) {
        String key = normalizeAction(action);
        if (key.isBlank()) return false;

        LuaValue exact = actionHandlers.get(key);
        if (exact != null) {
            invokeAction(exact, key, "", payload == null || payload.isBlank() ? key : payload);
            return true;
        }

        for (PrefixHandler handler : prefixHandlers) {
            if (key.startsWith(handler.prefix())) {
                String value = key.substring(handler.prefix().length());
                invokeAction(handler.handler(), key, handler.prefix(), value);
                return true;
            }
        }

        if (document.runAction(key, payload)) return true;
        return callGlobalFunction(key, payload);
    }

    public void execute(String source, String chunkName) {
        if (source == null || source.isBlank()) return;
        try {
            globals.load(source, chunkName == null || chunkName.isBlank() ? "html-dom.lua" : chunkName).call();
        } catch (LuaError error) {
            throw new IllegalStateException("Lua script failed: " + (chunkName == null ? "html-dom.lua" : chunkName), error);
        }
    }

    public boolean call(String functionName, String... args) {
        return callGlobalFunction(functionName, args);
    }

    public boolean callGlobalFunction(String functionName, String... args) {
        String clean = normalizeAction(functionName);
        if (clean.isBlank()) return false;
        LuaValue fn = globals.get(clean);
        if (fn.isnil() || !fn.isfunction()) return false;
        LuaValue[] values = new LuaValue[args == null ? 0 : args.length];
        for (int i = 0; i < values.length; i++) values[i] = LuaValue.valueOf(args[i] == null ? "" : args[i]);
        try {
            fn.invoke(values);
            return true;
        } catch (LuaError error) {
            throw new IllegalStateException("Lua function failed: " + clean, error);
        }
    }

    public void installInlineScripts() {
        int index = 0;
        if (document.dom().rootOptional().isEmpty()) return;
        for (UiDomElement script : UiDomTraversal.depthFirstElements(document.dom().documentElement())) {
            if (!"script".equals(script.tagName()) || !isLuaScript(script)) continue;
            if (!script.attribute("src", "").trim().isEmpty()) continue;
            execute(script.textContent(), "inline-script-" + index + ".lua");
            index++;
        }
    }

    public void installExternalScripts(HtmlDomScriptSourceResolver resolver) {
        if (resolver == null || document.dom().rootOptional().isEmpty()) return;
        for (UiDomElement script : UiDomTraversal.depthFirstElements(document.dom().documentElement())) {
            if (!"script".equals(script.tagName()) || !isLuaScript(script)) continue;
            String src = script.attribute("src", "").trim();
            if (src.isEmpty()) continue;
            try {
                execute(resolver.load(src), src);
            } catch (IOException error) {
                throw new IllegalStateException("HtmlDom Lua script could not be loaded: " + src, error);
            }
        }
    }

    private void invokeAction(LuaValue fn, String action, String prefix, String value) {
        try {
            LuaTable event = new LuaTable();
            event.set("action", action == null ? "" : action);
            event.set("prefix", prefix == null ? "" : prefix);
            event.set("value", value == null ? "" : value);
            event.set("payload", value == null ? "" : value);
            event.set("handledBy", "lua");
            fn.invoke(event, LuaValue.valueOf(value == null ? "" : value), LuaValue.valueOf(action == null ? "" : action));
        } catch (LuaError error) {
            throw new IllegalStateException("HtmlDom Lua action failed: " + action, error);
        }
    }

    private boolean isLuaScript(UiDomElement script) {
        String type = script.attribute("type", "").trim().toLowerCase(Locale.ROOT);
        String language = script.attribute("language", "").trim().toLowerCase(Locale.ROOT);
        return type.isEmpty()
                || "lua".equals(type)
                || "text/lua".equals(type)
                || "application/lua".equals(type)
                || "application/x-lua".equals(type)
                || "lua".equals(language);
    }

    private String normalizeAction(String action) {
        return action == null ? "" : action.trim();
    }

    private record PrefixHandler(String prefix, LuaValue handler) {
    }
}
