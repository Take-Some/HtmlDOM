package dev.takesome.htmldom.css.properties.paint;

import dev.takesome.htmldom.css.UiCssStringPropertySpec;

import java.util.Set;

/** CSS mask shorthand retained for backend-specific interpretation. */
public final class MaskCssProperty extends UiCssStringPropertySpec {
    public MaskCssProperty() {
        super("mask", Set.of("-webkit-mask"), false);
    }
}
