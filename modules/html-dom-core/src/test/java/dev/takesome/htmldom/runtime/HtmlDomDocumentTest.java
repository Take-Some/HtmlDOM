package dev.takesome.htmldom.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlDomDocumentTest {
    @Test
    void storesValuesBindingsAndActionsAroundCanonicalDom() {
        HtmlDomDocument document = HtmlDomDocument.parse("<html><body><button id=\"run\">Run</button></body></html>");
        HtmlDomMemoryValueBinding enabled = new HtmlDomMemoryValueBinding(false);

        document.bind("enabled", enabled)
                .value("count", 7)
                .action("increment", event -> event.document().value("count", 8));

        assertSame(document.dom(), document.uiDocument());
        assertTrue(document.getElementById("run").isPresent());
        assertEquals(false, document.value("enabled"));
        assertEquals("7", document.displayValue("count"));
        assertTrue(document.runAction("increment", "run"));
        assertEquals(8, document.value("count"));
    }
}
