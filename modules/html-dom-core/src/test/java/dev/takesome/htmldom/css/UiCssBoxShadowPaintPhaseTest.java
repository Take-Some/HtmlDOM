package dev.takesome.htmldom.css;

import dev.takesome.htmldom.markup.UiMarkupDocument;
import dev.takesome.htmldom.markup.UiMarkupParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UiCssBoxShadowPaintPhaseTest {
    @Test
    void boxShadowIsRegisteredAndEmitsPaintPhaseBeforeBackground() {
        UiCssPropertyRegistry registry = UiCssPropertyRegistry.loadBuiltins();
        assertTrue(registry.find("box-shadow").isPresent(), "box-shadow must be a registered CSS property");

        UiMarkupDocument markup = new UiMarkupParser().parse("<html><body><section id=\"card\"></section></body></html>");
        UiStylesheet stylesheet = UiCssUserAgentStylesheet.stylesheet().plus(new UiCssParser().parse("""
                #card {
                    display: block;
                    width: 120px;
                    height: 60px;
                    background-color: #ffffff;
                    box-shadow: 0 12px 26px #24406018;
                }
                """));
        new UiCssCascade().apply(markup.dom(), stylesheet);
        UiCssLayoutResult layout = new UiCssLayoutEngine().layout(markup.dom(), 640, 480);
        UiCssPaintTree tree = new UiCssPaintTreeBuilder().build(markup.dom(), layout);

        UiCssPaintNode card = tree.nodes().values().stream()
                .filter(node -> "card".equals(node.id()))
                .findFirst()
                .orElseThrow();

        assertTrue(card.phases().contains(UiCssPaintPhase.BOX_SHADOW));
        assertTrue(card.phases().indexOf(UiCssPaintPhase.BOX_SHADOW) < card.phases().indexOf(UiCssPaintPhase.BACKGROUND));
        assertEquals(UiCssStyleImpact.PAINT, UiCssStyleImpact.of("box-shadow"));
    }
}
