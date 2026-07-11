package dev.takesome.htmldom.css.properties.paint;

import dev.takesome.htmldom.css.UiCssBasePropertySpec;
import dev.takesome.htmldom.css.UiCssParseContext;
import dev.takesome.htmldom.css.UiCssValue;
import dev.takesome.htmldom.css.UiMaskImage;

import java.util.Set;

public final class MaskImageCssProperty extends UiCssBasePropertySpec {
    public MaskImageCssProperty() {
        super("mask-image", Set.of("-webkit-mask-image"), false);
    }

    @Override
    public UiCssValue initialValue() {
        return UiCssValue.typed(name(), UiMaskImage.none());
    }

    @Override
    public UiCssValue parse(UiCssParseContext context, String rawValue) {
        return UiCssValue.typed(name(), UiMaskImage.parse(rawValue));
    }
}
