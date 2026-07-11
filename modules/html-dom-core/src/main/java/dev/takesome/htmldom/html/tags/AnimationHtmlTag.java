package dev.takesome.htmldom.html.tags;

import dev.takesome.htmldom.html.UiHtmlBaseTagSpec;
import dev.takesome.htmldom.html.UiHtmlCommonAttributes;
import java.util.Set;

/** Declarative animation hook/container. Animation behavior is driven by CSS animation-* properties. */
public final class AnimationHtmlTag extends UiHtmlBaseTagSpec {
    public AnimationHtmlTag() {
        super("animation", Set.of("animate"), "animation", UiHtmlCommonAttributes.bindable(
                "name",
                "target",
                "duration",
                "delay",
                "timing-function",
                "iteration-count",
                "direction",
                "fill-mode",
                "play-state"
        ));
    }
}
