package dev.takesome.htmldom.css.properties.paint;

import dev.takesome.htmldom.css.UiCssDefinitionStatus;
import dev.takesome.htmldom.css.UiCssStringPropertySpec;

import java.util.Set;

/** HtmlDom rendering hint that multiplies the resolved mask alpha. */
public final class MaskOpacityCssProperty extends UiCssStringPropertySpec {
    public MaskOpacityCssProperty() {
        super("mask-opacity", Set.of("-webkit-mask-opacity"), false);
    }

    @Override
    public UiCssDefinitionStatus status() {
        return UiCssDefinitionStatus.EXPERIMENTAL;
    }
}
