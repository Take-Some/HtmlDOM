package dev.takesome.htmldom.desktop;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomEntryPointTest {
    @Test
    void markupEntryPointBuildsDesktopPanelAndInstallsInlineLua() {
        Dom dom = Dom.markup("""
                <html>
                    <body>
                        <p id="status">Idle</p>
                        <script type="lua">
                            dom.setText("status", "Booted")
                        </script>
                    </body>
                </html>
                """);

        dom.panel();

        assertEquals("Booted", dom.document().getElementById("status").orElseThrow().textContent());
    }

    @Test
    void appliesStructuredAndClasspathEnvironmentsBeforeLuaScripts() {
        Dom dom = Dom.markup("""
                <html>
                    <body>
                        <p id="status">Idle</p>
                        <script type="lua">
                            htmldom.set("attempts", htmldom.get("attempts") + 1)
                        </script>
                    </body>
                </html>
                """)
                .environment(Map.of("values", Map.of("mode", "map")))
                .envJson("{\"values\":{\"mode\":\"json\"}}")
                .environmentResource("html-dom/environment-test.json");

        dom.panel();

        assertEquals("json", dom.document().value("mode"));
        assertEquals("classpath", dom.document().value("source"));
        assertEquals(5, dom.document().value("attempts"));
        assertEquals("Environment loaded", dom.document().getElementById("status").orElseThrow().textContent());
        assertTrue(dom.document().getElementById("status").orElseThrow().classList().contains("ready"));
    }
}
