package dev.takesome.htmldom.css.properties.paint;

import dev.takesome.htmldom.css.UiCssDefinitionStatus;
import dev.takesome.htmldom.css.UiCssStringPropertySpec;

import java.util.Set;

/** Optional raster-backend sampling hint for software mask implementations. */
public final class MaskSampleSizeCssProperty extends UiCssStringPropertySpec {
    public MaskSampleSizeCssProperty() {
        super("mask-sample-size", Set.of("mask-resolution"), false);
    }

    @Override
    public UiCssDefinitionStatus status() {
        return UiCssDefinitionStatus.EXPERIMENTAL;
    }
}
