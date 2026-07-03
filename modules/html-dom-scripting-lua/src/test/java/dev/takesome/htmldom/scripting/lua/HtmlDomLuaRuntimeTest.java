package dev.takesome.htmldom.scripting.lua;

import dev.takesome.htmldom.runtime.HtmlDomDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlDomLuaRuntimeTest {
    @Test
    void exposesDocumentBindingsAndActionDispatchToLua() {
        HtmlDomDocument document = HtmlDomDocument.parse("<html><body><p id=\"status\">Idle</p></body></html>");
        HtmlDomLuaRuntime runtime = new HtmlDomLuaRuntime(document);

        runtime.execute("""
                htmldom.bind("mode", "idle")
                htmldom.on("demo.run", function(event)
                    htmldom.bind("mode", event.payload)
                    dom.setText("status", event.payload)
                end)
                """, "lua-runtime-test.lua");

        assertTrue(runtime.dispatchAction("demo.run", "armed"));
        assertEquals("armed", document.displayValue("mode"));
        assertEquals("armed", document.getElementById("status").orElseThrow().textContent());
    }
}
