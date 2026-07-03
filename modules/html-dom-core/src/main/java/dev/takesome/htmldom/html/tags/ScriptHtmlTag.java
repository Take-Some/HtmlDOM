package dev.takesome.htmldom.html.tags;

import dev.takesome.htmldom.html.UiHtmlBaseTagSpec;
import dev.takesome.htmldom.html.UiHtmlCommonAttributes;

import java.util.Set;

/** Metadata/runtime script tag. It is retained in the DOM but not treated as a visual control. */
public final class ScriptHtmlTag extends UiHtmlBaseTagSpec {
    public ScriptHtmlTag() {
        super("script", Set.of(), "script", UiHtmlCommonAttributes.styleRaw(
                "src", "type", "language", "charset", "async", "defer", "crossorigin", "integrity", "referrerpolicy"
        ));
    }
}
