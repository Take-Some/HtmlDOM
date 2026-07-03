# Desktop runtime

The desktop runtime lives in `html-dom-desktop`. It hosts a retained DOM document inside a Swing `JPanel` and renders through Java2D.

## Main entry points

| Class | Purpose |
| --- | --- |
| `Dom` | High-level resource-backed desktop document facade. Preferred for bundled UI documents. |
| `BundledHtmlUi` | Executable showcase launcher. |
| `HtmlDomSwingPanel` | Main host panel. Owns DOM, stylesheet, layout, Lua runtime and paint orchestration. |
| `HtmlDomInputRouter` | Mouse, keyboard, scroll, pointer and click routing. |
| `HtmlDomPaintEngine` | Paint helpers for background, border, outlines, shadows and paint metadata. |
| `HtmlDomTextPaintEngine` | Text and inline-box painting. |
| `HtmlDomControlPaintEngine` | Controls such as inputs, selects, progress and button content. |
| `HtmlDomTransformEngine` | Applies CSS transform-like operations to paint and hit-test coordinates. |
| `HtmlDomTransitionController` | Runtime interpolation for paint-only transitions. |

## Transform support

`HtmlDomTransformEngine` supports the practical subset used by the desktop renderer:

```text
translate / translateX / translateY
scale / scaleX / scaleY
rotate
matrix
transform-origin
```

Transforms are applied before background, border, content, outline and scrollbars. Hit-test shapes are registered under the same transformed graphics context.

## Transition support

`HtmlDomTransitionController` interpolates runtime style changes for:

```text
transform
opacity
color
border-color
background-color
```

When a transition finishes, `transitionend` is dispatched through the event dispatcher with property name and elapsed time.

## Pointer and mouse model

The input router maintains the current pointer target and dispatches:

```text
pointermove / mousemove
pointerover / mouseover
pointerout / mouseout
pointerenter / mouseenter
pointerleave / mouseleave
```

`pointer-events: none` is enforced in hit-test registration.

## DevTools integration

When DevTools highlights a node, `HtmlDomSwingPanel` paints a Chrome-like overlay over the live interface. The overlay includes selector, dimensions, content/padding/border/margin visualization and direct-child outlines.

## Dom facade

`dev.takesome.htmldom.desktop.Dom` is the preferred high-level desktop bootstrap API for resource-backed documents. It is intentionally not a mod API: it has no Forge, Minecraft, screen or game-runtime dependencies. The facade provides:

- safe classpath/filesystem document loading;
- explicit resource bases for co-located UI assets;
- constructor CSS and explicit stylesheet resources;
- document-declared and explicit Lua script loading;
- runtime `value`, `bind`, `action` and neutral host-bridge registration.

The lower-level `HtmlDomSwingPanel` constructors remain valid for direct embedding and tests. See [Desktop Dom entry point](dom-entry-point.md) for the complete facade API.
