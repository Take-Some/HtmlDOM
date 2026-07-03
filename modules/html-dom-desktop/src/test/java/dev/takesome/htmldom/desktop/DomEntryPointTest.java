package dev.takesome.htmldom.desktop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomEntryPointTest {
    @Test
    void markupEntryPointBuildsDesktopPanelAndInstallsInlineLua() {
        Dom dom = Dom.markup("""
                <html>
                    <body>
                        <p id=\"status\">Idle</p>
                        <script type=\"lua\">
                            dom.setText("status", "Booted")
                        </script>
                    </body>
                </html>
                """);

        dom.panel();

        assertEquals("Booted", dom.document().getElementById("status").orElseThrow().textContent());
    }
}
