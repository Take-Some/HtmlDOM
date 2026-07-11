package dev.takesome.htmldom.css.properties.paint;

import dev.takesome.htmldom.css.UiCssStringPropertySpec;

import java.util.Set;

public final class MaskRepeatCssProperty extends UiCssStringPropertySpec {
    public MaskRepeatCssProperty() {
        super("mask-repeat", Set.of("-webkit-mask-repeat"), false);
    }
}
