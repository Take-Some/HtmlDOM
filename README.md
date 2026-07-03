<p align="center">
  <img src="docs/images/github-header.svg" alt="HtmlDom — Desktop HTML-like UI" width="900"/>
</p>

# HtmlDom

Desktop HTML-like UI library extracted into a standalone multi-module repository.

This is a desktop stack, not browser/WebView: retained DOM, HTML-like markup, CSS cascade/layout, Lua scripting as the JavaScript replacement, font registry, Font Awesome registry, and a Swing/JFrame renderer.


## Release identity

- Current version: `1.0.12`
- Release name: `CSS Length Tolerance Hotfix`
- Authors: `Take Some()`
- Primary package group: `dev.takesome`


## Screenshots

<p align="center">
  <img src="docs/images/devtools-runtime.svg" alt="HtmlDom desktop UI and DevTools runtime" width="900"/>
</p>


## Documentation

- [Architecture overview](docs/architecture.md)
- [Desktop runtime](docs/desktop-runtime.md)
- [Desktop Dom entry point](docs/dom-entry-point.md)
- [CSS, layout and paint pipeline](docs/css-layout-paint.md)
- [DevTools and runtime editing](docs/devtools.md)
- [Lua scripting ABI](docs/lua-scripting.md)
- [Packages and release process](docs/packages-and-release.md)
- [1.0.12 CSS Length Tolerance Hotfix notes](docs/releases/1.0.12-css-length-tolerance-hotfix.md)
- [1.0.1 Documentary Release notes](docs/releases/1.0.1-documentary-release.md)
- [Authors](AUTHORS.md)

## Modules

- `html-dom-core` — DOM, HTML registry, markup parser, CSS parser/cascade/layout, user-agent styles.
- `html-dom-fonts` — font registry and built-in classpath font registration.
- `html-dom-icons-fontawesome` — Font Awesome icon registry, glyph descriptors, TTF resources and font registration hooks.
- `html-dom-scripting-lua` — Lua runtime and DOM bindings; Lua is used instead of browser JavaScript.
- `html-dom-devtools` — DOM/layout/line/inline/paint/scroll inspection snapshots.
- `html-dom-desktop` — Swing/JFrame renderer and bundled showcase launcher.

## Font resources

Built-in fonts are registered from:

```text
modules/html-dom-fonts/src/main/resources/html-dom/fonts/built-in-fonts.json
```

The bundled registry loads the copied FS Elliot / Roboto Mono font files from `Java2DGame`.

## Font Awesome

Font Awesome TTFs are copied into:

```text
modules/html-dom-icons-fontawesome/src/main/resources/html-dom/icons/fontawesome/
```

The desktop renderer registers them through `FontAwesomeFonts.register(HtmlDomFonts.registry())`, then resolves icon classes such as:

```html
<i class="fa-solid fa-code"></i>
<i class="fa-solid fa-bolt"></i>
```

## Lua instead of JS

Bundled Lua lives at:

```text
modules/html-dom-desktop/src/main/resources/html-dom/bundled/showcase.lua
```

The desktop runtime loads scripts through `HtmlDomLuaRuntime`; scripts can mutate DOM through `dom` and interact with document state through `htmldom`.


## Desktop Dom entry point

`html-dom-desktop` now exposes a Helix-style, desktop-safe `Dom` facade. It loads markup from classpath or filesystem resources, keeps document-owned `<link rel="stylesheet">` CSS loading in the desktop renderer, installs Lua scripts declared by `<script type="lua">`, and supports neutral runtime values/actions without Minecraft or Forge dependencies.

```java
HtmlDomSwingPanel panel = Dom.classpath("html-dom/bundled/showcase.ui.html")
        .resourceBase("html-dom/bundled/")
        .script("showcase.lua")
        .value("enabled", true)
        .action("ui.refresh", event -> event.document().value("lastAction", event.action()))
        .panel();
```

Inline markup remains available for tools, tests and embedded desktop shells:

```java
HtmlDomDocument document = Dom.markup("<html><body><p id=\"status\">Ready</p></body></html>")
        .document();
```

## Build

```bat
gradlew.bat clean compileJava --console=plain --no-daemon
```

## Run desktop showcase

```bat
gradlew.bat :html-dom-desktop:run --console=plain --no-daemon
```

## Build executable showcase jar

```bat
gradlew.bat bundledHtmlUiJar --console=plain --no-daemon
```

Run it:

```bat
java -jar modules\html-dom-desktop\build\libs\html-dom-ui-1.0.12-bundled.jar
```

## Paint tree / scroll containers

The core layout result now carries block boxes, line boxes, inline boxes and scroll boxes. The paint layer is split into physical phases:

```text
paintBackground
paintBorder
paintContent
paintOutline
paintPositionedDescendants
paintScrollbars
```

DevTools snapshots include layout nodes, paint nodes and scroll container nodes with content size, viewport size and scroll offsets.


## Maven package

Artifacts are published as GitHub Packages when a release tag such as `v1.0.12` is pushed.

Gradle repository setup:

```gradle
repositories {
    maven {
        url = uri('https://maven.pkg.github.com/Take-Some/JavaDOM')
        credentials {
            username = findProperty('gpr.user') ?: System.getenv('GITHUB_ACTOR')
            password = findProperty('gpr.key') ?: System.getenv('GITHUB_TOKEN')
        }
    }
}
```

Primary dependency examples:

```gradle
dependencies {
    // All-in-one executable desktop runtime package:
    implementation 'dev.takesome:html-dom-aio:1.0.12'

    // Split module packages:
    implementation 'dev.takesome:html-dom-core:1.0.12'
    implementation 'dev.takesome:html-dom-desktop:1.0.12'
    implementation 'dev.takesome:html-dom-fonts:1.0.12'
    implementation 'dev.takesome:html-dom-icons-fontawesome:1.0.12'
    implementation 'dev.takesome:html-dom-scripting-lua:1.0.12'
    implementation 'dev.takesome:html-dom-devtools:1.0.12'
}
```

Build the AIO jar locally:

```bat
gradlew.bat :html-dom-desktop:aioJar --console=plain --no-daemon
java -jar modules\html-dom-desktop\build\libs\html-dom-aio-1.0.12.jar
```

Local verification:

```bat
gradlew.bat publishToMavenLocal --console=plain --no-daemon
```


## Authors

- Take Some()

## CI / release / packages

GitHub Actions are configured in:

```text
.github/workflows/ci.yml
.github/workflows/release.yml
```

- `CI` runs on `main` and pull requests.
- `Release` runs on version tags such as `v1.0.12`.
- Maven artifacts are published to GitHub Packages under `dev.takesome:*:1.0.12`, including `dev.takesome:html-dom-aio:1.0.12`.
- The bundled desktop showcase jar is attached to the GitHub release.

Create a release locally:

```bat
git tag -a v1.0.12 -m "HtmlDom 1.0.12 — CSS Length Tolerance Hotfix"
git push origin main v1.0.12
```
