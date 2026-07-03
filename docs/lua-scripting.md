# Lua scripting ABI

HtmlDom uses Lua instead of browser JavaScript. The Lua runtime is provided by `html-dom-scripting-lua`.

## Runtime location

Bundled showcase scripts live at:

```text
modules/html-dom-desktop/src/main/resources/html-dom/bundled/showcase.lua
```

## Click hooks

The desktop panel calls Lua hooks around native action handling:

```lua
function onClick(action, elementId)
    -- return true to consume native handling
end

function afterClick(action, elementId)
end
```

## Transition hooks

When a transition finishes, DevTools/event runtime can call:

```lua
function onTransitionEnd(propertyName, targetId, currentTargetId, elapsedMs)
end
```

## DOM mutation

The Lua binding exposes a `dom` table for document lookup and mutation. Runtime mutations are visible to cascade/layout/paint on the next repaint cycle.


## Runtime tables

Lua scripts run inside `HtmlDomLuaRuntime`. The runtime exposes three tables:

| Table | Purpose |
| --- | --- |
| `dom` | DOM lookup and mutation helpers. |
| `htmldom` | Runtime values, bindings and action dispatch. |
| `host` | Calls into the neutral `HtmlDomHostBridge`. |

Core DOM helpers:

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

Runtime state and action handlers:

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

Host calls are intentionally application-neutral:

```lua
host.close()
host.openDocument("settings.html")
host.openUrl("https://example.invalid")
```

Full bootstrap details are documented in [Desktop Dom entry point](dom-entry-point.md).
