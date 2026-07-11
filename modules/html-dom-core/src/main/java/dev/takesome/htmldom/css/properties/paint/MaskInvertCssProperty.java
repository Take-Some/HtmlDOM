package dev.takesome.htmldom.css.properties.paint;

import dev.takesome.htmldom.css.UiCssDefinitionStatus;
import dev.takesome.htmldom.css.UiCssStringPropertySpec;

import java.util.Set;

/** HtmlDom rendering hint that inverts the resolved mask alpha. */
public final class MaskInvertCssProperty extends UiCssStringPropertySpec {
    public MaskInvertCssProperty() {
        super("mask-invert", Set.of("-webkit-mask-invert"), false);
    }

    @Override
    public UiCssDefinitionStatus status() {
        return UiCssDefinitionStatus.EXPERIMENTAL;
    }
}
