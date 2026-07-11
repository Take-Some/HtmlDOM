package dev.takesome.htmldom.css;

import dev.takesome.htmldom.css.properties.paint.BackgroundCssProperty;
import dev.takesome.htmldom.css.properties.paint.BackgroundImageCssProperty;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class UiCssUrlNormalizerTest {
    @Test
    void stripsCssUrlQuotesForBackgroundImageProperty() {
        UiCssValue value = new BackgroundImageCssProperty().parse(
                new UiCssParseContext(),
                "url(\"smarthostiles:textures/gui/background.png\")"
        );

        assertEquals("smarthostiles:textures/gui/background.png", value.require(UiBackgroundImage.class).source());
    }

    @Test
    void extractsQuotedUrlFromBackgroundShorthandWithoutChangingCssSyntax() {
        List<UiCssDeclaration> declarations = new BackgroundCssProperty().expand(
                new UiCssParseContext(),
                "url(\"smarthostiles:textures/gui/background.png\") center / cover no-repeat, var(--helix-bg-bottom, #C8E6FA)"
        );

        assertEquals("smarthostiles:textures/gui/background.png", declaration(declarations, "background-image"));
        assertEquals("center", declaration(declarations, "background-position"));
        assertEquals("cover", declaration(declarations, "background-size"));
        assertEquals("no-repeat", declaration(declarations, "background-repeat"));
    }

    @Test
    void supportsSingleQuotedAndUnquotedUrlValues() {
        assertEquals("smarthostiles:textures/gui/a.png", UiCssUrlNormalizer.normalizeUrlValue("url('smarthostiles:textures/gui/a.png')"));
        assertEquals("smarthostiles:textures/gui/b.png", UiCssUrlNormalizer.normalizeUrlValue("url(smarthostiles:textures/gui/b.png)"));
    }

    private static String declaration(List<UiCssDeclaration> declarations, String property) {
        return declarations.stream()
                .filter(declaration -> property.equals(declaration.property()))
                .findFirst()
                .orElseThrow()
                .value();
    }
}
