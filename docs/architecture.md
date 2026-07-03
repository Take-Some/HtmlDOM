# HtmlDom architecture

HtmlDom is a desktop HTML-like UI stack. It is not a browser, a WebView wrapper or a server-rendered UI. The runtime is built around a retained DOM, CSS parsing/cascade/layout, a Java2D paint pipeline, Lua scripting and a custom desktop DevTools surface.

## Module map

| Module | Responsibility |
| --- | --- |
| `html-dom-logging` | Shared logging facade and console logging backend. |
| `html-dom-core` | DOM model, runtime document wrapper, HTML registry, markup parser, CSS parser, cascade, layout, paint tree metadata and user-agent styles. |
| `html-dom-fonts` | Font registry and built-in classpath font loading. |
| `html-dom-icons-fontawesome` | Font Awesome font resources, icon registry and icon class resolution. |
| `html-dom-scripting-lua` | Lua runtime and DOM bindings. Lua is the scripting layer instead of browser JavaScript. |
| `html-dom-devtools` | Immutable inspection snapshots for layout, paint, scroll and hit-test data. |
| `html-dom-desktop` | Swing/JFrame host, Java2D renderer, input routing, DevTools window and bundled showcase launcher. |

## Runtime flow

```text
Dom.classpath / Dom.file / Dom.markup
  -> HtmlDomResourceResolver
  -> UiMarkupParser
  -> UiDomDocument
  -> HtmlDomDocument
  -> HtmlDomLuaRuntime
  -> UiCssCascade
  -> UiCssLayoutEngine
  -> HtmlDomSwingPanel
  -> Java2D paint phases
```

Lua can mutate the retained DOM at runtime. Runtime values and actions are stored in `HtmlDomDocument`; after mutation the panel reapplies cascade/layout and repaints.

## Paint phases

The desktop renderer paints in explicit phases:

```text
background
border
content
outline
positioned descendants
scrollbars
fixed layer
overlays
DevTools highlight overlay
```

The phases are intentionally separate. This keeps hit-test registration, scroll containers, overlays and DevTools inspection deterministic.

## Event model

The desktop input router converts Swing events into HtmlDom events. The event dispatcher supports capture, target and bubble phases, plus non-bubbling `mouseenter`, `mouseleave`, `pointerenter` and `pointerleave`.

The event dispatcher also keeps a bounded event log used by DevTools.
