package dev.takesome.htmldom.css.properties.paint;

import dev.takesome.htmldom.css.UiCssStringPropertySpec;

import java.util.Set;

public final class MaskSizeCssProperty extends UiCssStringPropertySpec {
    public MaskSizeCssProperty() {
        super("mask-size", Set.of("-webkit-mask-size"), false);
    }
}
