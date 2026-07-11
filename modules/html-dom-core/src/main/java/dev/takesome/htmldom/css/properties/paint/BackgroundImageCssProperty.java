package dev.takesome.htmldom.css.properties.paint;


import dev.takesome.htmldom.css.UiBackgroundImage;
import dev.takesome.htmldom.css.UiCssBasePropertySpec;
import dev.takesome.htmldom.css.UiCssParseContext;
import dev.takesome.htmldom.css.UiCssValue;
import dev.takesome.htmldom.css.UiCssUrlNormalizer;
import java.util.Set;

public final class BackgroundImageCssProperty extends UiCssBasePropertySpec {
    public BackgroundImageCssProperty() {
        super("back" + "ground-image", Set.of(), true);
    }

    public UiCssValue initialValue() {
        return UiCssValue.typed(name(), UiBackgroundImage.none());
    }

    public UiCssValue parse(UiCssParseContext context, String rawValue) {
        String source = UiCssUrlNormalizer.normalizeUrlValue(rawValue);
        return UiCssValue.typed(name(), source.isBlank() || "none".equalsIgnoreCase(source) ? UiBackgroundImage.none() : new UiBackgroundImage(source));
    }
}
