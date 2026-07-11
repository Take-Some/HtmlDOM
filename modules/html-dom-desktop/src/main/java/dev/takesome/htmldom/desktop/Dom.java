package dev.takesome.htmldom.desktop;

import dev.takesome.htmldom.desktop.resource.HtmlDomResourceBundle;
import dev.takesome.htmldom.desktop.resource.HtmlDomResourceKind;
import dev.takesome.htmldom.desktop.resource.HtmlDomResourceNamespace;
import dev.takesome.htmldom.desktop.resource.HtmlDomResourceResolver;
import dev.takesome.htmldom.runtime.HtmlDomAction;
import dev.takesome.htmldom.runtime.HtmlDomDocument;
import dev.takesome.htmldom.runtime.HtmlDomHostBridge;
import dev.takesome.htmldom.runtime.HtmlDomValueBinding;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Desktop-safe entry point for HtmlDom documents.
 *
 * <p>This mirrors the HelixUI {@code Dom} ergonomics without depending on Minecraft,
 * Forge, resource locations, screens, matrix stacks or game config classes. Documents
 * are loaded from the classpath or filesystem, linked CSS is still handled by the
 * desktop renderer, and Lua scripts are installed through the HtmlDom scripting layer.</p>
 */
public final class Dom {
    private static final String DEFAULT_INLINE_DOCUMENT = "inline.html";
    private static final Pattern SCRIPT_BLOCK = Pattern.compile("<script\\b([^>]*)>(.*?)</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SCRIPT_SELF_CLOSING = Pattern.compile("<script\\b([^>]*)/\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "([A-Za-z_:][A-Za-z0-9_:.\\-]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+))",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final String documentRequest;
    private final String inlineMarkup;
    private final List<String> styleRequests = new ArrayList<>();
    private final List<String> stylesheetSources = new ArrayList<>();
    private final List<String> scriptRequests = new ArrayList<>();
    private final List<String> inlineScripts = new ArrayList<>();
    private final List<String> resourceBasePaths = new ArrayList<>();
    private final List<BindingRegistration> bindings = new ArrayList<>();
    private final List<ValueRegistration> values = new ArrayList<>();
    private final List<Map<String, ?>> environments = new ArrayList<>();
    private final List<String> jsonEnvironments = new ArrayList<>();
    private final List<String> environmentRequests = new ArrayList<>();
    private final List<ActionRegistration> actions = new ArrayList<>();
    private HtmlDomHostBridge bridge = HtmlDomHostBridge.NOOP;
    private HtmlDomConfig config = HtmlDomConfig.defaults();
    private HtmlDomClient client = HtmlDomClient.DEFAULT;
    private ClassLoader classLoader = Dom.class.getClassLoader();
    private HtmlDomSwingPanel panel;

    public Dom(String document) {
        this(safeResource(document), null);
    }

    private Dom(String documentRequest, String inlineMarkup) {
        this.documentRequest = documentRequest == null || documentRequest.isBlank() ? DEFAULT_INLINE_DOCUMENT : documentRequest;
        this.inlineMarkup = inlineMarkup;
    }

    public static Dom document(String document) {
        return new Dom(document);
    }

    public static Dom classpath(String document) {
        return new Dom(document);
    }

    public static Dom file(Path document) {
        Objects.requireNonNull(document, "document");
        return new Dom(safeResource(document.toAbsolutePath().normalize().toString()));
    }

    public static Dom markup(String source) {
        String markup = source == null || source.isBlank() ? "<document></document>" : source;
        return new Dom(DEFAULT_INLINE_DOCUMENT, markup);
    }

    public Dom classLoader(ClassLoader classLoader) {
        this.classLoader = classLoader == null ? Dom.class.getClassLoader() : classLoader;
        this.panel = null;
        return this;
    }

    public Dom config(HtmlDomConfig config) {
        this.config = config == null ? HtmlDomConfig.defaults() : config;
        this.panel = null;
        return this;
    }

    public Dom client(HtmlDomClient client) {
        this.client = client == null ? HtmlDomClient.DEFAULT : client;
        this.panel = null;
        return this;
    }

    public Dom bridge(HtmlDomHostBridge bridge) {
        this.bridge = bridge == null ? HtmlDomHostBridge.NOOP : bridge;
        if (panel != null) panel.bridge(this.bridge);
        return this;
    }

    public Dom resourceBase(String basePath) {
        String clean = safeResource(basePath);
        if (!clean.isBlank()) resourceBasePaths.add(clean);
        this.panel = null;
        return this;
    }

    /** Adds an explicit stylesheet resource. Document-owned {@code <link>} CSS is loaded automatically by the panel. */
    public Dom style(String stylesheetResource) {
        styleRequests.add(safeResource(stylesheetResource));
        this.panel = null;
        return this;
    }

    /** Adds raw CSS to the constructor stylesheet chunk. */
    public Dom stylesheet(String css) {
        if (css != null && !css.isBlank()) stylesheetSources.add(css);
        this.panel = null;
        return this;
    }

    public Dom css(String css) {
        return stylesheet(css);
    }

    public Dom script(String scriptResource) {
        scriptRequests.add(safeResource(scriptResource));
        this.panel = null;
        return this;
    }

    public Dom inlineScript(String luaSource) {
        if (luaSource != null && !luaSource.isBlank()) inlineScripts.add(luaSource);
        this.panel = null;
        return this;
    }

    public Dom bind(String name, HtmlDomValueBinding binding) {
        if (name != null && !name.trim().isEmpty() && binding != null) {
            BindingRegistration registration = new BindingRegistration(name.trim(), binding);
            bindings.add(registration);
            if (panel != null) panel.bind(registration.name(), registration.binding());
        }
        return this;
    }

    public Dom value(String name, Object value) {
        if (name != null && !name.trim().isEmpty()) {
            ValueRegistration registration = new ValueRegistration(name.trim(), value);
            values.add(registration);
            if (panel != null) panel.value(registration.name(), registration.value());
        }
        return this;
    }

    public Dom values(Map<String, ?> values) {
        if (values != null && !values.isEmpty()) {
            for (Map.Entry<String, ?> entry : new LinkedHashMap<>(values).entrySet()) {
                value(entry.getKey(), entry.getValue());
            }
        }
        return this;
    }

    public Dom environment(Map<String, ?> environment) {
        if (environment != null && !environment.isEmpty()) {
            Map<String, ?> copy = new LinkedHashMap<>(environment);
            environments.add(copy);
            if (panel != null) panel.htmlDocument().environment(copy);
        }
        return this;
    }

    public Dom environment(String json) {
        return envJson(json);
    }

    public Dom envJson(String json) {
        if (json != null && !json.isBlank()) {
            jsonEnvironments.add(json);
            if (panel != null) panel.htmlDocument().envJson(json);
        }
        return this;
    }

    public Dom environmentResource(String resource) {
        environmentRequests.add(safeResource(resource));
        this.panel = null;
        return this;
    }

    public Dom action(String name, HtmlDomAction action) {
        if (name != null && !name.trim().isEmpty() && action != null) {
            ActionRegistration registration = new ActionRegistration(name.trim(), action);
            actions.add(registration);
            if (panel != null) panel.action(registration.name(), registration.action());
        }
        return this;
    }

    public HtmlDomDocument document() {
        return panel().htmlDocument();
    }

    public HtmlDomSwingPanel panel() {
        if (panel == null) panel = buildPanel();
        return panel;
    }

    private HtmlDomSwingPanel buildPanel() {
        String sourcePath = documentRequest;
        String basePath = documentBase(sourcePath);
        HtmlDomResourceBundle bundle = resourceBundle(sourcePath);
        HtmlDomResourceResolver resolver = new HtmlDomResourceResolver(classLoader, bundle);
        String markup = loadMarkup(resolver);
        String css = explicitCss(resolver, basePath);
        HtmlDomSwingPanel built = new HtmlDomSwingPanel(markup, css, sourcePath, basePath, config, mergedClient(bundle));
        built.bridge(bridge);
        applyRuntimeRegistrations(built);
        applyEnvironmentResources(built, resolver, basePath);
        installDocumentScripts(built, markup, resolver, basePath);
        return built;
    }

    private String loadMarkup(HtmlDomResourceResolver resolver) {
        if (inlineMarkup != null) return inlineMarkup;
        return resolver.resolveText(documentRequest, HtmlDomResourceKind.MARKUP)
                .orElseThrow(() -> new IllegalStateException("HtmlDom document resource not found: " + documentRequest));
    }

    private String explicitCss(HtmlDomResourceResolver resolver, String basePath) {
        StringBuilder out = new StringBuilder();
        for (String css : stylesheetSources) {
            if (!css.isBlank()) out.append('\n').append(css).append('\n');
        }
        for (String request : styleRequests) {
            String css = resolver.resolveText(request, HtmlDomResourceKind.CSS, basePath)
                    .orElseThrow(() -> new IllegalStateException("HtmlDom stylesheet resource not found: " + request));
            out.append('\n').append(css).append('\n');
        }
        return out.toString();
    }

    private void applyRuntimeRegistrations(HtmlDomSwingPanel target) {
        for (BindingRegistration registration : bindings) target.bind(registration.name(), registration.binding());
        for (ValueRegistration registration : values) target.value(registration.name(), registration.value());
        for (Map<String, ?> environment : environments) target.htmlDocument().environment(environment);
        for (String json : jsonEnvironments) target.htmlDocument().envJson(json);
        for (ActionRegistration registration : actions) target.action(registration.name(), registration.action());
    }

    private void applyEnvironmentResources(HtmlDomSwingPanel target, HtmlDomResourceResolver resolver, String basePath) {
        for (String request : environmentRequests) {
            String json = resolver.resolveText(request, HtmlDomResourceKind.ENVIRONMENT, basePath)
                    .orElseThrow(() -> new IllegalStateException("HtmlDom environment resource not found: " + request));
            target.htmlDocument().envJson(json);
        }
    }

    private void installDocumentScripts(HtmlDomSwingPanel target, String markup, HtmlDomResourceResolver resolver, String basePath) {
        List<ScriptDescriptor> documentScripts = documentScripts(markup);
        int inlineIndex = 0;
        for (ScriptDescriptor script : documentScripts) {
            if (script.src().isBlank() && !script.body().isBlank()) {
                target.executeLua(script.body(), "inline-script-" + inlineIndex + ".lua");
                inlineIndex++;
            }
        }
        for (ScriptDescriptor script : documentScripts) {
            if (!script.src().isBlank()) {
                String source = resolver.resolveText(script.src(), HtmlDomResourceKind.SCRIPT, basePath)
                        .orElseThrow(() -> new IllegalStateException("HtmlDom Lua script resource not found: " + script.src()));
                target.executeLua(source, script.src());
            }
        }
        for (String request : scriptRequests) {
            String source = resolver.resolveText(request, HtmlDomResourceKind.SCRIPT, basePath)
                    .orElseThrow(() -> new IllegalStateException("HtmlDom Lua script resource not found: " + request));
            target.executeLua(source, request);
        }
        int explicitInlineIndex = 0;
        for (String source : inlineScripts) {
            target.executeLua(source, "dom-inline-script-" + explicitInlineIndex + ".lua");
            explicitInlineIndex++;
        }
    }

    private List<ScriptDescriptor> documentScripts(String markup) {
        String source = markup == null ? "" : markup;
        ArrayList<ScriptDescriptor> scripts = new ArrayList<>();
        Matcher blockMatcher = SCRIPT_BLOCK.matcher(source);
        while (blockMatcher.find()) {
            Map<String, String> attributes = attributes(blockMatcher.group(1));
            if (!isLuaScript(attributes)) continue;
            scripts.add(new ScriptDescriptor(safeOptionalResource(attributes.getOrDefault("src", "")), blockMatcher.group(2) == null ? "" : blockMatcher.group(2)));
        }
        Matcher selfClosingMatcher = SCRIPT_SELF_CLOSING.matcher(source);
        while (selfClosingMatcher.find()) {
            Map<String, String> attributes = attributes(selfClosingMatcher.group(1));
            if (!isLuaScript(attributes)) continue;
            String src = safeOptionalResource(attributes.getOrDefault("src", ""));
            if (!src.isBlank()) scripts.add(new ScriptDescriptor(src, ""));
        }
        return scripts;
    }

    private HtmlDomResourceBundle resourceBundle(String sourcePath) {
        ArrayList<String> bases = new ArrayList<>(resourceBasePaths);
        return HtmlDomResourceBundle.forDocument(sourcePath, bases.toArray(new String[0]));
    }

    private HtmlDomClient mergedClient(HtmlDomResourceBundle bundle) {
        HtmlDomClient delegate = client == null ? HtmlDomClient.DEFAULT : client;
        return new HtmlDomClient() {
            @Override
            public HtmlDomResourceBundle configureResources(String sourcePath, String baseResourcePath, HtmlDomResourceBundle defaults) {
                HtmlDomResourceBundle merged = mergeBundles(bundle, defaults);
                HtmlDomResourceBundle configured = delegate.configureResources(sourcePath, baseResourcePath, merged);
                return configured == null ? merged : configured;
            }

            @Override
            public void onAfterCreated(HtmlDomSurface surface) {
                delegate.onAfterCreated(surface);
            }

            @Override
            public void onBeforeClose(HtmlDomSurface surface) {
                delegate.onBeforeClose(surface);
            }

            @Override
            public void onLifecycleEvent(HtmlDomLifecycleEvent event) {
                delegate.onLifecycleEvent(event);
            }
        };
    }

    private HtmlDomResourceBundle mergeBundles(HtmlDomResourceBundle first, HtmlDomResourceBundle second) {
        ArrayList<HtmlDomResourceNamespace> namespaces = new ArrayList<>();
        if (first != null) namespaces.addAll(first.namespaces());
        if (second != null) namespaces.addAll(second.namespaces());
        boolean strict = (first != null && first.strict()) || (second != null && second.strict());
        return new HtmlDomResourceBundle(namespaces, strict);
    }

    private static Map<String, String> attributes(String raw) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(raw == null ? "" : raw);
        while (matcher.find()) {
            String key = matcher.group(1);
            if (key == null || key.isBlank()) continue;
            String value = matcher.group(3);
            if (value == null) value = matcher.group(4);
            if (value == null) value = matcher.group(5);
            result.put(key.toLowerCase(Locale.ROOT), value == null ? "" : value.trim());
        }
        return result;
    }

    private static boolean isLuaScript(Map<String, String> attributes) {
        String type = attributes.getOrDefault("type", "").trim().toLowerCase(Locale.ROOT);
        String language = attributes.getOrDefault("language", "").trim().toLowerCase(Locale.ROOT);
        return type.isEmpty()
                || "lua".equals(type)
                || "text/lua".equals(type)
                || "application/lua".equals(type)
                || "application/x-lua".equals(type)
                || "lua".equals(language);
    }

    private static String safeOptionalResource(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        return safeResource(value);
    }

    private static String safeResource(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("HtmlDom resource path must not be blank");
        }
        String clean = normalizePath(value);
        String lower = clean.toLowerCase(Locale.ROOT);
        if (clean.indexOf('\0') >= 0) throw new IllegalArgumentException("HtmlDom resource path contains NUL: " + value);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("jar:") || lower.startsWith("data:") || lower.startsWith("//")) {
            throw new IllegalArgumentException("External HtmlDom resource schemes are not allowed: " + value);
        }
        if (clean.contains("?") || clean.contains("#")) {
            throw new IllegalArgumentException("HtmlDom resource query and fragment are not allowed: " + value);
        }
        if (clean.equals("..") || clean.startsWith("../") || clean.contains("/../") || clean.endsWith("/..")) {
            throw new IllegalArgumentException("Unsafe HtmlDom parent traversal in resource path: " + value);
        }
        return clean;
    }

    private static String documentBase(String sourcePath) {
        String source = normalizePath(sourcePath);
        int slash = source.lastIndexOf('/');
        return slash < 0 ? "" : source.substring(0, slash);
    }

    private static String normalizePath(String raw) {
        if (raw == null) return "";
        return raw.trim().replace('\\', '/').replaceAll("/++", "/");
    }

    private record BindingRegistration(String name, HtmlDomValueBinding binding) {
    }

    private record ValueRegistration(String name, Object value) {
    }

    private record ActionRegistration(String name, HtmlDomAction action) {
    }

    private record ScriptDescriptor(String src, String body) {
    }
}
