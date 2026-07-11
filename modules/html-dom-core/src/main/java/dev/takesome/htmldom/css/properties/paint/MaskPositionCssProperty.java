package dev.takesome.htmldom.css.properties.paint;

import dev.takesome.htmldom.css.UiCssStringPropertySpec;

import java.util.Set;

public final class MaskPositionCssProperty extends UiCssStringPropertySpec {
    public MaskPositionCssProperty() {
        super("mask-position", Set.of("-webkit-mask-position"), false);
    }
}
