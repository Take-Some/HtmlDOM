package dev.takesome.htmldom.runtime;

import dev.takesome.htmldom.dom.UiDomElement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HtmlDomEnvironmentTest {
    @Test
    void appliesJsonValuesElementsAndActionsWithoutPlatformTypes() {
        HtmlDomDocument document = HtmlDomDocument.parse("""
                <html><body>
                    <h1 id="title">Old title</h1>
                    <button id="start" disabled="true">Start</button>
                </body></html>
                """);

        document.envJson("""
                {
                  "values": {
                    "player": {"name": "Kayla", "level": 7},
                    "enabled": true
                  },
                  "byId": {
                    "#title": {
                      "$text": "Frozen Lands",
                      "class+": "active highlighted",
                      "data-state": "ready"
                    },
                    "start": {
                      "disabled": null,
                      "attributes": {"aria-label": "Launch game"}
                    }
                  },
                  "actions": {"start": "game.start"}
                }
                """);

        assertEquals("Kayla", document.value("player.name"));
        assertEquals(7, document.value("player.level"));
        assertEquals(true, document.value("enabled"));

        UiDomElement title = document.getElementById("title").orElseThrow();
        assertEquals("Frozen Lands", title.textContent());
        assertTrue(title.classList().contains("active"));
        assertTrue(title.classList().contains("highlighted"));
        assertEquals("ready", title.attribute("data-state", ""));

        UiDomElement start = document.getElementById("start").orElseThrow();
        assertFalse(start.hasAttribute("disabled"));
        assertEquals("Launch game", start.attribute("aria-label", ""));
        assertEquals("game.start", start.attribute("action", ""));
    }

    @Test
    void acceptsMapEnvironmentAndBulkValues() {
        HtmlDomDocument document = HtmlDomDocument.parse("<html><body><div id=\"status\"></div></body></html>")
                .values(Map.of("ready", false, "count", 2))
                .environment(Map.of(
                        "values", Map.of("session", Map.of("name", "test")),
                        "elements", Map.of("status", Map.of("$text", "Ready"))
                ));

        assertEquals(false, document.value("ready"));
        assertEquals(2, document.value("count"));
        assertEquals("test", document.value("session.name"));
        assertEquals("Ready", document.getElementById("status").orElseThrow().textContent());
    }

    @Test
    void rejectsMalformedJsonAndNonObjectRoots() {
        HtmlDomDocument document = HtmlDomDocument.parse("<html><body></body></html>");

        assertThrows(IllegalArgumentException.class, () -> document.envJson("{\"values\":"));
        assertThrows(IllegalArgumentException.class, () -> document.envJson("[1, 2, 3]"));
    }

    @Test
    void serializesStructuredAttributeValuesAsJson() {
        HtmlDomDocument document = HtmlDomDocument.parse("<html><body><div id=\"node\"></div></body></html>")
                .environment(Map.of("elements", Map.of(
                        "node", Map.of("data-config", Map.of("enabled", true, "levels", new int[] {1, 2}))
                )));

        String json = document.getElementById("node").orElseThrow().attribute("data-config", "");
        assertEquals(Map.of("enabled", true, "levels", List.of(1, 2)), HtmlDomJsonEnvironment.parseObject(json));
    }
}
