package dev.takesome.htmldom.scripting.lua;

import dev.takesome.htmldom.dom.UiDomDocument;
import dev.takesome.htmldom.dom.UiDomElement;
import dev.takesome.htmldom.runtime.HtmlDomDocument;
import dev.takesome.htmldom.runtime.HtmlDomValueBinding;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** DOM functions exposed to Lua scripts. */
public final class HtmlDomLuaDomBindings {
    private HtmlDomLuaDomBindings() {
    }

    public static void install(Globals globals, UiDomDocument document) {
        install(null, globals, new HtmlDomDocument(document));
    }

    static void install(HtmlDomLuaRuntime runtime, Globals globals, HtmlDomDocument document) {
        LuaTable dom = new LuaTable();
        bindDom(dom, runtime, document);
        globals.set("dom", dom);

        LuaTable htmlDom = new LuaTable();
        bindDom(htmlDom, runtime, document);
        htmlDom.set("bind", new DomFunction(runtime, document, "bind"));
        htmlDom.set("value", new DomFunction(runtime, document, "bind"));
        htmlDom.set("runAction", new DomFunction(runtime, document, "runAction"));
        htmlDom.set("on", new DomFunction(runtime, document, "on"));
        htmlDom.set("onPrefix", new DomFunction(runtime, document, "onPrefix"));
        globals.set("htmldom", htmlDom);

        LuaTable host = new LuaTable();
        host.set("close", new DomFunction(runtime, document, "hostClose"));
        host.set("openDocument", new DomFunction(runtime, document, "hostOpenDocument"));
        host.set("openUrl", new DomFunction(runtime, document, "hostOpenUrl"));
        globals.set("host", host);
    }

    private static void bindDom(LuaTable table, HtmlDomLuaRuntime runtime, HtmlDomDocument document) {
        table.set("exists", new DomFunction(runtime, document, "exists"));
        table.set("attr", new DomFunction(runtime, document, "getAttribute"));
        table.set("getAttr", new DomFunction(runtime, document, "getAttribute"));
        table.set("getAttribute", new DomFunction(runtime, document, "getAttribute"));
        table.set("setAttr", new DomFunction(runtime, document, "setAttribute"));
        table.set("setAttribute", new DomFunction(runtime, document, "setAttribute"));
        table.set("removeAttr", new DomFunction(runtime, document, "removeAttribute"));
        table.set("removeAttribute", new DomFunction(runtime, document, "removeAttribute"));
        table.set("text", new DomFunction(runtime, document, "text"));
        table.set("setText", new DomFunction(runtime, document, "setText"));
        table.set("addClass", new DomFunction(runtime, document, "addClass"));
        table.set("removeClass", new DomFunction(runtime, document, "removeClass"));
        table.set("toggleClass", new DomFunction(runtime, document, "toggleClass"));
        table.set("bind", new DomFunction(runtime, document, "bind"));
        table.set("runAction", new DomFunction(runtime, document, "runAction"));
    }

    private static final class DomFunction extends VarArgFunction {
        private final HtmlDomLuaRuntime runtime;
        private final HtmlDomDocument document;
        private final String command;

        private DomFunction(HtmlDomLuaRuntime runtime, HtmlDomDocument document, String command) {
            this.runtime = runtime;
            this.document = document;
            this.command = command;
        }

        @Override
        public Varargs invoke(Varargs args) {
            return switch (command) {
                case "exists" -> element(args, 1) == null ? LuaValue.FALSE : LuaValue.TRUE;
                case "text" -> {
                    UiDomElement element = element(args, 1);
                    yield element == null ? LuaValue.NIL : LuaValue.valueOf(element.textContent());
                }
                case "setText" -> {
                    UiDomElement element = element(args, 1);
                    if (element == null) yield LuaValue.FALSE;
                    element.clearChildren();
                    element.appendChild(document.dom().createText(stringArg(args, 2)));
                    yield LuaValue.TRUE;
                }
                case "addClass" -> classOp(args, "add");
                case "removeClass" -> classOp(args, "remove");
                case "toggleClass" -> classOp(args, "toggle");
                case "getAttribute" -> {
                    UiDomElement element = element(args, 1);
                    yield element == null ? LuaValue.NIL : LuaValue.valueOf(element.attribute(stringArg(args, 2), ""));
                }
                case "setAttribute" -> {
                    UiDomElement element = element(args, 1);
                    if (element == null) yield LuaValue.FALSE;
                    element.setAttribute(stringArg(args, 2), stringArg(args, 3));
                    yield LuaValue.TRUE;
                }
                case "removeAttribute" -> {
                    UiDomElement element = element(args, 1);
                    if (element == null) yield LuaValue.FALSE;
                    element.removeAttribute(stringArg(args, 2));
                    yield LuaValue.TRUE;
                }
                case "bind" -> bind(args);
                case "runAction" -> runtime == null ? LuaValue.FALSE : LuaValue.valueOf(runtime.dispatchAction(stringArg(args, 1), stringArg(args, 2)));
                case "on" -> {
                    if (runtime == null || !args.arg(2).isfunction()) yield LuaValue.FALSE;
                    runtime.on(stringArg(args, 1), args.arg(2));
                    yield LuaValue.TRUE;
                }
                case "onPrefix" -> {
                    if (runtime == null || !args.arg(2).isfunction()) yield LuaValue.FALSE;
                    runtime.onPrefix(stringArg(args, 1), args.arg(2));
                    yield LuaValue.TRUE;
                }
                case "hostClose" -> {
                    document.bridge().close(document);
                    yield LuaValue.TRUE;
                }
                case "hostOpenDocument" -> {
                    document.bridge().openDocument(document, stringArg(args, 1));
                    yield LuaValue.TRUE;
                }
                case "hostOpenUrl" -> {
                    document.bridge().openUrl(document, stringArg(args, 1));
                    yield LuaValue.TRUE;
                }
                default -> LuaValue.NIL;
            };
        }

        private Varargs classOp(Varargs args, String op) {
            UiDomElement element = element(args, 1);
            if (element == null) return LuaValue.FALSE;
            String token = stringArg(args, 2);
            switch (op) {
                case "add" -> element.classList().add(token);
                case "remove" -> element.classList().remove(token);
                case "toggle" -> element.classList().toggle(token);
                default -> { return LuaValue.FALSE; }
            }
            return LuaValue.TRUE;
        }

        private Varargs bind(Varargs args) {
            String name = stringArg(args, 1);
            if (name.isBlank()) return LuaValue.NIL;
            if (!args.arg(2).isnil()) {
                document.value(name, javaValue(args.arg(2)));
            }
            HtmlDomValueBinding binding = document.binding(name);
            return binding == null ? LuaValue.NIL : LuaValue.valueOf(binding.displayValue());
        }

        private Object javaValue(LuaValue value) {
            if (value == null || value.isnil()) return null;
            if (value.isboolean()) return value.toboolean();
            if (value.isint()) return value.toint();
            if (value.isnumber()) return value.todouble();
            return value.tojstring();
        }

        private String stringArg(Varargs args, int index) {
            LuaValue value = args.arg(index);
            return value.isnil() ? "" : value.tojstring();
        }

        private UiDomElement element(Varargs args, int index) {
            return element(stringArg(args, index));
        }

        private UiDomElement element(String selectorOrId) {
            if (selectorOrId == null || selectorOrId.isBlank()) return null;
            String clean = selectorOrId.trim();
            if (clean.startsWith("#") || clean.startsWith(".") || clean.contains("[") || clean.contains(":") || clean.contains(" ") || clean.contains(">")) {
                return document.querySelector(clean).orElse(null);
            }
            UiDomElement byId = document.getElementById(clean).orElse(null);
            if (byId != null) return byId;
            return document.querySelector(clean).orElse(null);
        }
    }
}
