package dev.takesome.htmldom.devtools;

import dev.takesome.htmldom.css.UiCssCascade;
import dev.takesome.htmldom.css.UiCssLayoutEngine;
import dev.takesome.htmldom.css.UiCssLayoutResult;
import dev.takesome.htmldom.css.UiCssParser;
import dev.takesome.htmldom.css.UiCssPropertyRegistry;
import dev.takesome.htmldom.css.UiCssUserAgentStylesheet;
import dev.takesome.htmldom.css.UiStylesheet;
import dev.takesome.htmldom.markup.UiMarkupDocument;
import dev.takesome.htmldom.markup.UiMarkupParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class HtmlDomDevToolsCompatibilityTest {
    @Test
    void snapshotsRemainCompatibleWithExtendedPaintPhasesAndServiceProviders() {
        UiCssPropertyRegistry registry = UiCssPropertyRegistry.loadBuiltins();
        assertTrue(registry.find("box-shadow").isPresent());
        assertTrue(registry.find("mask-image").isPresent());

        UiMarkupDocument markup = new UiMarkupParser().parse("""
                <html><body>
                    <animation id="transition" name="fade-in">
                        <section id="card">Card</section>
                    </animation>
                </body></html>
                """);
        UiStylesheet stylesheet = UiCssUserAgentStylesheet.stylesheet().plus(new UiCssParser().parse("""
                #card {
                    display: block;
                    width: 200px;
                    height: 80px;
                    background-color: #ffffff;
                    box-shadow: 0 8px 18px #00000044;
                    mask-image: linear-gradient(to bottom, #ffffff, transparent);
                }
                """));
        new UiCssCascade().apply(markup.dom(), stylesheet);
        UiCssLayoutResult layout = new UiCssLayoutEngine().layout(markup.dom(), 800, 600);

        HtmlDomDevToolsSnapshot snapshot = HtmlDomDevTools.snapshot(markup.dom(), layout);
        HtmlDomDevToolsPaintNode card = snapshot.paintNodes().stream()
                .filter(node -> node.selector().contains("#card"))
                .findFirst()
                .orElseThrow();

        assertTrue(card.phases().contains("BOX_SHADOW"));
        assertTrue(card.phases().contains("CONTENT"));
        assertTrue(card.phases().contains("MASK"));
        assertTrue(snapshot.paintNodeCount() > 0);
        assertTrue(snapshot.layoutBoxCount() > 0);
    }
}
