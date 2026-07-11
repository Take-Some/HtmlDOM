package dev.takesome.htmldom.css;

import dev.takesome.htmldom.dom.UiDomElement;
import dev.takesome.htmldom.markup.UiMarkupDocument;
import dev.takesome.htmldom.markup.UiMarkupParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UiCssMaskResolverTest {
    @Test
    void registersMaskPropertiesAndResolvesRendererNeutralDescriptor() {
        UiCssPropertyRegistry registry = UiCssPropertyRegistry.loadBuiltins();
        assertTrue(registry.find("mask-image").isPresent());
        assertTrue(registry.find("-webkit-mask-image").isPresent());
        assertTrue(registry.find("mask-opacity").isPresent());

        UiMarkupDocument markup = new UiMarkupParser().parse("<html><body><section id=\"masked\"></section></body></html>");
        UiStylesheet stylesheet = UiCssUserAgentStylesheet.stylesheet().plus(new UiCssParser().parse("""
                #masked {
                    display: block;
                    width: 240px;
                    height: 120px;
                    background-color: #ffffff;
                    -webkit-mask-image: url("ui:masks/soft-edge.png");
                    mask-mode: luminance;
                    mask-repeat: no-repeat;
                    mask-size: cover;
                    mask-position: center;
                    mask-opacity: 75%;
                    mask-invert: true;
                    mask-sample-size: 3;
                }
                """));
        new UiCssCascade().apply(markup.dom(), stylesheet);

        UiDomElement masked = markup.dom().getElementById("masked").orElseThrow();
        UiCssMask mask = UiCssMaskResolver.resolve(masked);

        assertTrue(mask.active());
        assertEquals(UiMaskImage.Kind.URL, mask.image().kind());
        assertEquals("ui:masks/soft-edge.png", mask.image().source());
        assertEquals(UiMaskMode.LUMINANCE, mask.mode());
        assertEquals(UiMaskRepeat.NO_REPEAT, mask.repeat());
        assertEquals("cover", mask.size());
        assertEquals("center", mask.position());
        assertEquals(0.75D, mask.opacity(), 0.0001D);
        assertTrue(mask.invert());
        assertEquals(3, mask.sampleSize());
    }

    @Test
    void maskCreatesPaintPhaseAndStackingContextWithoutBackendDependency() {
        UiMarkupDocument markup = new UiMarkupParser().parse("<html><body><section id=\"masked\"></section></body></html>");
        UiStylesheet stylesheet = UiCssUserAgentStylesheet.stylesheet().plus(new UiCssParser().parse("""
                #masked {
                    display: block;
                    width: 120px;
                    height: 60px;
                    mask-image: linear-gradient(to bottom, transparent, #ffffff);
                }
                """));
        new UiCssCascade().apply(markup.dom(), stylesheet);
        UiCssLayoutResult layout = new UiCssLayoutEngine().layout(markup.dom(), 640, 480);
        UiCssPaintNode masked = new UiCssPaintTreeBuilder().build(markup.dom(), layout).order().stream()
                .filter(node -> "masked".equals(node.id()))
                .findFirst()
                .orElseThrow();

        assertTrue(masked.stackingContext());
        assertTrue(masked.phases().contains(UiCssPaintPhase.MASK));
        assertTrue(masked.phases().indexOf(UiCssPaintPhase.CONTENT) < masked.phases().indexOf(UiCssPaintPhase.MASK));
    }
}
