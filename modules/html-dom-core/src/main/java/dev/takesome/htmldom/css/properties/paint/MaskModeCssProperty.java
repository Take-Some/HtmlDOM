package dev.takesome.htmldom.css.properties.paint;

import dev.takesome.htmldom.css.UiCssStringPropertySpec;

import java.util.Set;

public final class MaskModeCssProperty extends UiCssStringPropertySpec {
    public MaskModeCssProperty() {
        super("mask-mode", Set.of("-webkit-mask-mode"), false);
    }
}
