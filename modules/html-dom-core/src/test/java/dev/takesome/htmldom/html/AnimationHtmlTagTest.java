package dev.takesome.htmldom.html;

import dev.takesome.htmldom.markup.UiMarkupDocument;
import dev.takesome.htmldom.markup.UiMarkupParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AnimationHtmlTagTest {
    @Test
    void animationTagIsRegisteredAsNormalContainerElement() {
        UiHtmlTagRegistry registry = UiHtmlTagRegistry.loadBuiltins();
        UiHtmlTagMeta meta = UiHtmlTagMeta.inferred(registry.require("animation"));

        assertEquals("animation", meta.name());
        assertFalse(meta.voidTag(), "animation element must require an explicit closing tag");
    }

    @Test
    void parserAcceptsAnimationElementWithClosingTag() {
        UiMarkupDocument document = new UiMarkupParser().parse("<html><body><animation id=\"fade\" name=\"helix-rise-in\"><section id=\"target\"></section></animation></body></html>");

        assertFalse(document.hasDiagnostics(), "valid animation markup must not produce diagnostics");
        assertTrue(document.dom().getElementById("fade").isPresent());
        assertEquals("animation", document.dom().getElementById("fade").orElseThrow().tagName());
    }
}
