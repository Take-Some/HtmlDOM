# Desktop Dom entry point

`dev.takesome.htmldom.desktop.Dom` is the high-level bootstrap API for desktop HtmlDom applications. It brings the ergonomic document-loading model from HelixUI into HtmlDom without importing Minecraft, Forge, `Screen`, `ResourceLocation`, game config, or mod lifecycle logic.

The facade is a convenience layer over the existing desktop runtime:

```text
Dom
  -> resource resolver
  -> UiMarkupParser
  -> UiDomDocument
  -> HtmlDomDocument runtime wrapper
  -> HtmlDomLuaRuntime
  -> HtmlDomSwingPanel
```

`UiDomDocument` remains the canonical retained DOM. `HtmlDomDocument` adds runtime values, actions and host integration around that DOM.

## Basic usage

Load a classpath-backed document and create a Swing panel:

```java
HtmlDomSwingPanel panel = Dom.classpath("html-dom/bundled/showcase.ui.html")
        .resourceBase("html-dom/bundled/")
        .script("showcase.lua")
        .panel();
```

Use inline markup for tools, tests and embedded shells:

```java
HtmlDomDocument document = Dom.markup("""
        <html>
            <body>
                <p id="status">Ready</p>
            </body>
        </html>
        """).document();
```

Use a filesystem document when a desktop application owns UI files outside the JAR:

```java
HtmlDomSwingPanel panel = Dom.file(Path.of("ui/main.html"))
        .resourceBase("ui")
        .panel();
```

## Resource model

`Dom` accepts classpath and filesystem resources. It rejects external web/data/archive schemes, query strings, fragments and parent traversal before loading.

Accepted examples:

```text
html-dom/bundled/showcase.ui.html
ui/main.html
C:/tools/my-shell/ui/main.html
```

Rejected examples:

```text
https://example.com/ui.html
file:///tmp/ui.html?debug=true
../secret.html
data:text/html,...
jar:file:...
```

`resourceBase(...)` adds lookup roots for co-located assets. This lets the document reference local CSS, images and Lua scripts without hardcoding absolute paths.

## CSS loading

Document-owned CSS remains part of the desktop renderer pipeline. The facade does not replace `HtmlDomStylesheetLoader`; it feeds it correctly.

```html
<html>
    <head>
        <link rel="stylesheet" href="main.css" />
        <style>
            #status { color: #ffffff; }
        </style>
    </head>
</html>
```

Additional CSS can be injected from Java:

```java
Dom.classpath("ui/main.html")
        .resourceBase("ui")
        .stylesheet("#status { font-weight: 700; }")
        .style("overrides.css")
        .panel();
```

## Lua loading

`Dom` installs Lua scripts declared in the document and scripts registered from Java.

```html
<script type="lua" src="main.lua"></script>
<script type="lua">
    dom.setText("status", "Booted")
</script>
```

```java
Dom.classpath("ui/main.html")
        .resourceBase("ui")
        .script("main.lua")
        .inlineScript("dom.setText('status', 'Injected')")
        .panel();
```

The `<script>` tag is registered as a metadata/runtime HTML tag. It is retained in the DOM and does not need to fall back to a visual `<div>` node.

## Runtime values and actions

Runtime state belongs to `HtmlDomDocument`:

```java
Dom dom = Dom.classpath("ui/main.html")
        .value("enabled", true)
        .action("ui.refresh", event -> {
            event.document().value("lastAction", event.action());
        });

HtmlDomDocument document = dom.document();
Object enabled = document.value("enabled");
```

Actions can be triggered from Java:

```java
dom.panel().runAction("ui.refresh");
```

And from Lua:

```lua
htmldom.runAction("ui.refresh", "button-refresh")
```

## Lua API

The Lua runtime exposes three tables: `dom`, `htmldom` and `host`.

### `dom`

`dom` mutates/query-selects retained DOM elements:

```lua
dom.exists("status")
dom.text("status")
dom.setText("status", "Running")
dom.attr("status", "class")
dom.setAttr("status", "data-state", "running")
dom.removeAttr("status", "data-state")
dom.addClass("status", "active")
dom.removeClass("status", "active")
dom.toggleClass("status", "active")
```

Element arguments may be IDs or selectors:

```lua
dom.setText("status", "By id")
dom.setText("#status", "By selector")
dom.addClass(".panel", "active")
```

### `htmldom`

`htmldom` owns document runtime state and action dispatch:

```lua
htmldom.bind("mode", "idle")

htmldom.on("demo.run", function(event)
    htmldom.bind("mode", event.payload)
    dom.setText("status", event.payload)
end)

htmldom.onPrefix("tab:", function(event)
    dom.setText("active-tab", event.value)
end)
```

Action event fields:

```text
event.action
event.prefix
event.value
event.payload
event.handledBy
```

### `host`

`host` delegates to a neutral `HtmlDomHostBridge` implementation:

```lua
host.close()
host.openDocument("settings.html")
host.openUrl("https://example.invalid")
```

The default bridge is a no-op. Desktop applications can provide their own host behavior:

```java
Dom.classpath("ui/main.html")
        .bridge(new HtmlDomHostBridge() {
            @Override
            public void close(HtmlDomDocument document) {
                // close application shell
            }
        });
```

## Direct `HtmlDomSwingPanel` embedding

The lower-level constructors remain valid:

```java
HtmlDomSwingPanel panel = new HtmlDomSwingPanel(markup, css, sourcePath, resourceBase);
panel.value("mode", "idle");
panel.action("ui.apply", event -> event.document().value("applied", true));
panel.executeLua("dom.setText('status', 'Ready')", "inline.lua");
```

Use `Dom` when the UI should be loaded as a document bundle. Use direct constructors for generated markup, tests, custom loaders or legacy embedding.

## Migration from HelixUI

Do not port Minecraft-facing pieces into HtmlDom:

| HelixUI concept | HtmlDom desktop equivalent |
| --- | --- |
| `Dom.mod(namespace).document(path)` | `Dom.classpath(path)` or `Dom.file(path)` |
| `ResourceLocation` | safe string/path resource request |
| `HelixDocument` | `HtmlDomDocument` + `UiDomDocument` |
| `HelixValueBinding` | `HtmlDomValueBinding` |
| `HelixAction` | `HtmlDomAction` |
| `HelixHostBridge` | `HtmlDomHostBridge` |
| `HelixScreen` | `HtmlDomSwingPanel` / host window |
| Forge config helpers | application-owned bindings |

The rule is strict: HtmlDom owns DOM, CSS, Lua, resources and desktop rendering. Mods and games own their integration layer outside this repository.

## Verification

Relevant regression tests:

```text
modules/html-dom-core/src/test/java/dev/takesome/htmldom/runtime/HtmlDomDocumentTest.java
modules/html-dom-scripting-lua/src/test/java/dev/takesome/htmldom/scripting/lua/HtmlDomLuaRuntimeTest.java
modules/html-dom-desktop/src/test/java/dev/takesome/htmldom/desktop/DomEntryPointTest.java
```

Useful local checks:

```bat
gradlew.bat :html-dom-core:test --no-parallel --console=plain --no-daemon
gradlew.bat :html-dom-scripting-lua:test --no-parallel --console=plain --no-daemon
gradlew.bat :html-dom-desktop:test --tests dev.takesome.htmldom.desktop.DomEntryPointTest --no-parallel --console=plain --no-daemon
```
